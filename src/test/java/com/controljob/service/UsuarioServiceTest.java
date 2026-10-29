package com.controljob.service;

import com.controljob.model.PerfilUsuario;
import com.controljob.model.Usuario;
import com.controljob.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptService loginAttemptService;

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(repository, passwordEncoder, loginAttemptService, "admin");
    }

    @Test
    void precisaConfigurarQuandoNaoHaUsuarios() {
        when(repository.count()).thenReturn(0L);
        assertThat(service.precisaConfigurarAdministrador()).isTrue();

        when(repository.count()).thenReturn(1L);
        assertThat(service.precisaConfigurarAdministrador()).isFalse();
    }

    @Test
    void configuraAdministradorComSenhaForte() {
        when(repository.count()).thenReturn(0L);
        when(passwordEncoder.encode("senha-forte")).thenReturn("hash");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.configurarAdministrador("senha-forte");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNomeUsuario()).isEqualTo("admin");
        assertThat(captor.getValue().getSenhaHash()).isEqualTo("hash");
        assertThat(captor.getValue().getPerfil()).isEqualTo(PerfilUsuario.ADMIN);
    }

    @Test
    void rejeitaSenhaCurtaSemTocarNoBanco() {
        assertThatThrownBy(() -> service.configurarAdministrador("1234567"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.configurarAdministrador(null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void naoReconfiguraAdministradorExistente() {
        when(repository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.configurarAdministrador("senha-forte"))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void criaLeitorNormalizandoNomeECifrandoSenha() {
        when(repository.findByNomeUsuario("leitor.teste")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha-forte")).thenReturn("hash");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.criarLeitor("  Leitor.Teste  ", "senha-forte");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNomeUsuario()).isEqualTo("leitor.teste");
        assertThat(captor.getValue().getSenhaHash()).isEqualTo("hash");
        assertThat(captor.getValue().getPerfil()).isEqualTo(PerfilUsuario.LEITOR);
    }

    @Test
    void rejeitaLeitorDuplicado() {
        when(repository.findByNomeUsuario("leitor.teste")).thenReturn(Optional.of(new Usuario()));

        assertThatThrownBy(() -> service.criarLeitor("leitor.teste", "senha-forte"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já está em uso");
        verify(repository, never()).save(any());
    }

    @Test
    void listaUsuariosEmOrdem() {
        List<Usuario> usuarios = List.of(new Usuario());
        when(repository.findAllByOrderByNomeUsuarioAsc()).thenReturn(usuarios);

        assertThat(service.listarUsuarios()).isSameAs(usuarios);
    }

    @Test
    void loadUserNormalizaNomeEConcedePerfil() {
        Usuario usuario = usuario("admin", "hash", PerfilUsuario.ADMIN);
        when(loginAttemptService.estaBloqueado("admin")).thenReturn(false);
        when(repository.findByNomeUsuario("admin")).thenReturn(Optional.of(usuario));

        UserDetails details = service.loadUserByUsername("  ADMIN ");

        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserBloqueadoLancaLockedException() {
        when(loginAttemptService.estaBloqueado("admin")).thenReturn(true);

        assertThatThrownBy(() -> service.loadUserByUsername("admin"))
                .isInstanceOf(LockedException.class);
    }

    @Test
    void loadUserInexistenteLancaUsernameNotFound() {
        when(loginAttemptService.estaBloqueado("ninguem")).thenReturn(false);
        when(repository.findByNomeUsuario("ninguem")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ninguem"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private Usuario usuario(String nome, String hash, PerfilUsuario perfil) {
        Usuario usuario = new Usuario();
        usuario.setNomeUsuario(nome);
        usuario.setSenhaHash(hash);
        usuario.setPerfil(perfil);
        return usuario;
    }
}
