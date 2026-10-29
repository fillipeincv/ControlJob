package com.controljob.service;

import com.controljob.support.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private TestClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-10-29T12:00:00Z"), FUSO);
        service = new LoginAttemptService(clock);
    }

    @Test
    void bloqueiaAposCincoFalhas() {
        for (int i = 0; i < 5; i++) {
            service.registrarFalha("admin");
        }

        assertThat(service.estaBloqueado("admin")).isTrue();
        assertThat(service.falhasDe("admin")).isEqualTo(5);
    }

    @Test
    void naoBloqueiaAntesDeCincoFalhas() {
        for (int i = 0; i < 4; i++) {
            service.registrarFalha("admin");
        }

        assertThat(service.estaBloqueado("admin")).isFalse();
        assertThat(service.falhasDe("admin")).isEqualTo(4);
    }

    @Test
    void desbloqueiaDepoisDaJanelaDeBloqueio() {
        for (int i = 0; i < 5; i++) {
            service.registrarFalha("admin");
        }

        clock.avancar(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(service.estaBloqueado("admin")).isFalse();
        assertThat(service.falhasDe("admin")).isZero();
    }

    @Test
    void sucessoZeraAsFalhas() {
        service.registrarFalha("admin");
        service.registrarFalha("admin");

        service.registrarSucesso("admin");

        assertThat(service.falhasDe("admin")).isZero();
        assertThat(service.estaBloqueado("admin")).isFalse();
    }

    @Test
    void falhasAntigasSaemDaJanela() {
        for (int i = 0; i < 4; i++) {
            service.registrarFalha("admin");
        }

        clock.avancar(Duration.ofMinutes(16));
        service.registrarFalha("admin");

        assertThat(service.falhasDe("admin")).isEqualTo(1);
        assertThat(service.estaBloqueado("admin")).isFalse();
    }

    @Test
    void usuariosSaoIndependentesECaseInsensitive() {
        for (int i = 0; i < 5; i++) {
            service.registrarFalha("  ADMIN ");
        }

        assertThat(service.estaBloqueado("admin")).isTrue();
        assertThat(service.estaBloqueado("outro")).isFalse();
    }

    @Test
    void limiteDeTentativasEExposto() {
        assertThat(service.maxTentativas()).isEqualTo(5);
    }
}
