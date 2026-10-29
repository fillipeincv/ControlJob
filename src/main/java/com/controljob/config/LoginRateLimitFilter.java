package com.controljob.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUISICOES = 60;
    private static final Duration JANELA = Duration.ofMinutes(15);

    private final Map<String, JanelaIp> janelas = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicInteger requisicoes = new AtomicInteger();

    public LoginRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!deveLimitar(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        long agora = clock.millis();

        JanelaIp janela = janelas.compute(ip, (chave, atual) -> {
            if (atual == null || atual.inicio() <= agora - JANELA.toMillis()) {
                return new JanelaIp(agora, 1);
            }
            return new JanelaIp(atual.inicio(), atual.contagem() + 1);
        });

        if (janela.contagem() > MAX_REQUISICOES) {
            response.setStatus(429);
            return;
        }

        if (requisicoes.incrementAndGet() % 1000 == 0) {
            long limite = agora - JANELA.toMillis();
            janelas.entrySet().removeIf(entrada -> entrada.getValue().inicio() <= limite);
        }

        filterChain.doFilter(request, response);
    }

    private boolean deveLimitar(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("POST")
                && request.getRequestURI().equals(request.getContextPath() + "/login");
    }

    private record JanelaIp(long inicio, int contagem) {
    }
}