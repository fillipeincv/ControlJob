package com.controljob.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_TENTATIVAS = 5;
    private static final Duration JANELA = Duration.ofMinutes(15);
    private static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private final Map<String, Tentativa> tentativas = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public int maxTentativas() {
        return MAX_TENTATIVAS;
    }

    public int falhasDe(String usuario) {
        Tentativa tentativa = tentativas.get(chave(usuario));
        return tentativa == null || bloqueioExpirado(tentativa) ? 0 : tentativa.falhas();
    }

    public void registrarFalha(String usuario) {
        long agora = clock.millis();
        tentativas.merge(chave(usuario), new Tentativa(agora, 1, -1), (atual, nova) -> {
            boolean reiniciar = agora - atual.primeiraFalha() >= JANELA.toMillis();
            long inicio = reiniciar ? agora : atual.primeiraFalha();
            int falhas = reiniciar ? 1 : atual.falhas() + 1;
            long bloqueio = falhas >= MAX_TENTATIVAS ? agora + BLOQUEIO.toMillis() : -1;
            return new Tentativa(inicio, falhas, bloqueio);
        });
    }

    public void registrarSucesso(String usuario) {
        tentativas.remove(chave(usuario));
    }

    public boolean estaBloqueado(String usuario) {
        Tentativa tentativa = tentativas.get(chave(usuario));
        if (tentativa == null) {
            return false;
        }
        long agora = clock.millis();
        if (tentativa.bloqueadoAte() > agora) {
            return true;
        }
        if (tentativa.bloqueadoAte() != -1 && tentativa.bloqueadoAte() <= agora) {
            tentativas.remove(chave(usuario));
        }
        return false;
    }

    private boolean bloqueioExpirado(Tentativa tentativa) {
        return tentativa.bloqueadoAte() != -1 && tentativa.bloqueadoAte() <= clock.millis();
    }

    private static String chave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase();
    }

    private record Tentativa(long primeiraFalha, int falhas, long bloqueadoAte) {
    }
}