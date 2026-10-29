package com.controljob.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TipoConsertoTest {

    @Test
    void aplicaPercentualCorretoPorTipo() {
        assertThat(TipoConserto.CONSERTOS_EM_GERAL.calcularComissao(new BigDecimal("100.00")))
                .isEqualByComparingTo("5.00");
        assertThat(TipoConserto.REPARO_DE_PLACA.calcularComissao(new BigDecimal("100.00")))
                .isEqualByComparingTo("50.00");
        assertThat(TipoConserto.CONSERTO_PARA_LOJISTAS.calcularComissao(new BigDecimal("80.00")))
                .isEqualByComparingTo("40.00");
    }

    @Test
    void valorNuloRetornaZero() {
        assertThat(TipoConserto.REPARO_DE_PLACA.calcularComissao(null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void percentuaisSaoOsDefinidosPelaRegraDeNegocio() {
        assertThat(TipoConserto.CONSERTOS_EM_GERAL.getPercentualComissao()).isEqualByComparingTo("0.05");
        assertThat(TipoConserto.REPARO_DE_PLACA.getPercentualComissao()).isEqualByComparingTo("0.50");
        assertThat(TipoConserto.CONSERTO_PARA_LOJISTAS.getPercentualComissao()).isEqualByComparingTo("0.50");
    }
}
