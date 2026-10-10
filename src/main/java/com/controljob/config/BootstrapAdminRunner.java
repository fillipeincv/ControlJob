package com.controljob.config;

import com.controljob.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UsuarioService usuarioService;
    private final String senhaInicial;

    public BootstrapAdminRunner(
            UsuarioService usuarioService,
            @Value("${controljob.admin.senha-inicial:}") String senhaInicial
    ) {
        this.usuarioService = usuarioService;
        this.senhaInicial = senhaInicial;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (senhaInicial == null || senhaInicial.isBlank()) {
            return;
        }
        if (usuarioService.precisaConfigurarAdministrador()) {
            usuarioService.configurarAdministrador(senhaInicial);
            log.info("Administrador '{}' criado a partir de CONTROLJOB_ADMIN_SENHA.",
                    usuarioService.getNomeAdministrador());
        }
    }
}