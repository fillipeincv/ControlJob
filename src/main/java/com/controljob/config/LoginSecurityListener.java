package com.controljob.config;

import com.controljob.service.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class LoginSecurityListener {

    private static final Logger log = LoggerFactory.getLogger(LoginSecurityListener.class);

    private final LoginAttemptService loginAttemptService;

    public LoginSecurityListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onFalha(AuthenticationFailureBadCredentialsEvent evento) {
        String usuario = evento.getAuthentication().getName();
        loginAttemptService.registrarFalha(usuario);
        int falhas = loginAttemptService.falhasDe(usuario);
        log.warn("Falha de login para o usuário '{}' ({} de {} tentativas permitidas).",
                usuario, falhas, loginAttemptService.maxTentativas());
    }

    @EventListener
    public void onSucesso(AuthenticationSuccessEvent evento) {
        String usuario = evento.getAuthentication().getName();
        loginAttemptService.registrarSucesso(usuario);
        log.info("Login bem-sucedido do usuário '{}'.", usuario);
    }
}