package com.controljob.config;

import com.controljob.service.UsuarioService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final LoginRateLimitFilter loginRateLimitFilter;

    public SecurityConfig(LoginRateLimitFilter loginRateLimitFilter) {
        this.loginRateLimitFilter = loginRateLimitFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioService usuarioService) throws Exception {
        return http
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/favicon.svg", "/login", "/configurar-admin/**").permitAll()
                        .requestMatchers("/h2-console/**").denyAll()
                        .requestMatchers("/usuarios/**", "/admin/**", "/consertos/novo", "/consertos/*/editar", "/consertos/*/excluir", "/consertos/*/retirada", "/consertos/*/retirada/**", "/relatorio").hasRole("ADMIN")
                        .requestMatchers("/consertos").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, exceptionAuth) -> {
                    response.sendRedirect(usuarioService.precisaConfigurarAdministrador() ? "/configurar-admin" : "/login");
                }))
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login"))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
