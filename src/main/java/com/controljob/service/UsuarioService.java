package com.controljob.service;

import com.controljob.model.PerfilUsuario;
import com.controljob.model.Usuario;
import com.controljob.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final String nomeAdministrador;

    public UsuarioService(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            @Value("${controljob.admin.usuario:admin}") String nomeAdministrador
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.nomeAdministrador = nomeAdministrador;
    }

    public boolean precisaConfigurarAdministrador() {
        return repository.count() == 0;
    }

    public String getNomeAdministrador() {
        return nomeAdministrador;
    }

    public synchronized void configurarAdministrador(String senha) {
        if (senha == null || senha.trim().length() < 8) {
            throw new IllegalArgumentException("A senha do administrador deve ter pelo menos 8 caracteres.");
        }
        if (!precisaConfigurarAdministrador()) {
            throw new IllegalStateException("O administrador já foi configurado.");
        }
        Usuario administrador = new Usuario();
        administrador.setNomeUsuario(nomeAdministrador);
        administrador.setSenhaHash(passwordEncoder.encode(senha.trim()));
        administrador.setPerfil(PerfilUsuario.ADMIN);
        repository.save(administrador);
    }

    @Transactional
    public void criarLeitor(String nomeUsuario, String senha) {
        String nomeNormalizado = nomeUsuario.trim().toLowerCase();
        if (repository.findByNomeUsuario(nomeNormalizado).isPresent()) {
            throw new IllegalArgumentException("Esse nome de usuário já está em uso.");
        }
        Usuario leitor = new Usuario();
        leitor.setNomeUsuario(nomeNormalizado);
        leitor.setSenhaHash(passwordEncoder.encode(senha));
        leitor.setPerfil(PerfilUsuario.LEITOR);
        repository.save(leitor);
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarUsuarios() {
        return repository.findAllByOrderByNomeUsuarioAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String nome = username.trim().toLowerCase();
        if (loginAttemptService.estaBloqueado(nome)) {
            throw new LockedException("Conta temporariamente bloqueada por excesso de tentativas.");
        }
        Usuario usuario = repository.findByNomeUsuario(nome)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));
        return User.withUsername(usuario.getNomeUsuario())
                .password(usuario.getSenhaHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name()))
                .build();
    }
}
