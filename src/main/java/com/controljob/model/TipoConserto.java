package com.controljob.model;

import java.math.BigDecimal;

public enum TipoConserto {

    CONSERTOS_EM_GERAL("Consertos em Geral", new BigDecimal("0.05")),
    REPARO_DE_PLACA("Reparo de Placa", new BigDecimal("0.50")),
    CONSERTO_PARA_LOJISTAS("Conserto para Lojistas", new BigDecimal("0.50"));

    private final String label;
    private final BigDecimal percentualComissao;

    TipoConserto(String label, BigDecimal percentualComissao) {
        this.label = label;
        this.percentualComissao = percentualComissao;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getPercentualComissao() {
        return percentualComissao;
    }

    public BigDecimal calcularComissao(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(percentualComissao);
    }
}
