package com.controljob.config;

import com.controljob.model.Conserto;
import com.controljob.model.PerfilUsuario;
import com.controljob.model.Usuario;
import com.controljob.repository.ConsertoRepository;
import com.controljob.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:controljob-seguranca;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "controljob.admin.senha-inicial=",
        "controljob.migracoes.retirada-historica=false",
        "controljob.auditoria.diretorio=${java.io.tmpdir}/controljob-test-auditoria"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConsertoRepository consertoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void prepararDados() {
        consertoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.save(usuario("admin", PerfilUsuario.ADMIN));
        usuarioRepository.save(usuario("leitor", PerfilUsuario.LEITOR));
    }

    @Test
    void anonimoRedirecionaParaLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginEConfiguracaoSaoPublicos() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mockMvc.perform(get("/css/motion.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/page-transition.js")).andExpect(status().isOk());
        mockMvc.perform(get("/configurar-admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "leitor", roles = "LEITOR")
    void leitorNaoAcessaAreasAdministrativas() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/auditoria")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/auditoria/csv")).andExpect(status().isForbidden());
        mockMvc.perform(get("/relatorio")).andExpect(status().isForbidden());
        mockMvc.perform(get("/consertos/novo")).andExpect(status().isForbidden());
        mockMvc.perform(get("/usuarios")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "leitor", roles = "LEITOR")
    void leitorAcessaPaginasDeConsulta() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/lojistas")).andExpect(status().isOk());
        mockMvc.perform(get("/historico")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAcessaAreasAdministrativas() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isOk());
        mockMvc.perform(get("/relatorio")).andExpect(status().isOk());
        mockMvc.perform(get("/usuarios")).andExpect(status().isOk());
        mockMvc.perform(get("/consertos/novo")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void consoleH2BloqueadoParaAdmin() throws Exception {
        mockMvc.perform(get("/h2-console/")).andExpect(status().isForbidden());
    }

    @Test
    void postSemCsrfEBloqueado() throws Exception {
        mockMvc.perform(post("/consertos")
                        .with(user("admin").roles("ADMIN"))
                        .param("modeloAparelho", "iPhone")
                        .param("numeroOs", "OS-1")
                        .param("tipoConserto", "CONSERTOS_EM_GERAL")
                        .param("descricao", "Teste")
                        .param("valor", "100.00")
                        .param("dataConserto", "2026-10-01"))
                .andExpect(status().isForbidden());

        assertThat(consertoRepository.count()).isZero();
    }

    @Test
    void formularioIgnoraCamposDeStatusNaoEditaveis() throws Exception {
        mockMvc.perform(post("/consertos")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("modeloAparelho", "iPhone")
                        .param("numeroOs", "OS-ASSIGNMENT")
                        .param("tipoConserto", "CONSERTOS_EM_GERAL")
                        .param("descricao", "Teste de mass assignment")
                        .param("valor", "100.00")
                        .param("dataConserto", "2026-10-01")
                        .param("dataRetirada", "2020-01-01")
                        .param("dataFechamento", "2020-01-01"))
                .andExpect(status().is3xxRedirection());

        Conserto salvo = consertoRepository.findAll().stream()
                .filter(c -> c.getNumeroOs().equals("OS-ASSIGNMENT"))
                .findFirst()
                .orElseThrow();
        assertThat(salvo.getDataRetirada()).isNull();
        assertThat(salvo.getDataFechamento()).isNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void consertoInexistenteRetornaPaginaDeErro() throws Exception {
        mockMvc.perform(get("/consertos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("erro"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void edicaoDeConsertoFechadoEBloqueada() throws Exception {
        Conserto fechado = new Conserto();
        fechado.setModeloAparelho("iPhone");
        fechado.setNumeroOs("OS-FECHADA");
        fechado.setTipoConserto(com.controljob.model.TipoConserto.CONSERTOS_EM_GERAL);
        fechado.setDescricao("Fechado");
        fechado.setValor(new java.math.BigDecimal("100.00"));
        fechado.setDataConserto(LocalDate.of(2026, 9, 1));
        fechado.setDataRetirada(LocalDate.of(2026, 9, 2));
        fechado.setDataFechamento(LocalDate.of(2026, 9, 30));
        fechado = consertoRepository.save(fechado);

        MvcResult result = mockMvc.perform(get("/consertos/" + fechado.getId() + "/editar"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo("/consertos/" + fechado.getId());
        assertThat(consertoRepository.findById(fechado.getId()).orElseThrow().getValor())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void migracaoDeRetiradaNaoRodaSemOptIn() {
        assertThat(applicationContext.getBeansOfType(RetiradaHistoricaRunner.class)).isEmpty();
    }

    private Usuario usuario(String nome, PerfilUsuario perfil) {
        Usuario usuario = new Usuario();
        usuario.setNomeUsuario(nome);
        usuario.setSenhaHash(passwordEncoder.encode("senha-forte"));
        usuario.setPerfil(perfil);
        return usuario;
    }
}
