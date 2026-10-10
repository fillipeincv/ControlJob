package com.controljob.model;

import java.math.BigDecimal;

public class DashboardResumo {

    private final BigDecimal valorTotalConsertos;
    private final BigDecimal totalComissao;
    private final BigDecimal salarioFixo;
    private final BigDecimal salarioTotal;
    private final long quantidadeConsertos;
    private final BigDecimal comissaoGerais;
    private final BigDecimal comissaoPlaca;
    private final BigDecimal comissaoLojistas;

    public DashboardResumo(
            BigDecimal valorTotalConsertos,
            BigDecimal totalComissao,
            BigDecimal salarioFixo,
            BigDecimal salarioTotal,
            long quantidadeConsertos,
            BigDecimal comissaoGerais,
            BigDecimal comissaoPlaca,
            BigDecimal comissaoLojistas
    ) {
        this.valorTotalConsertos = valorTotalConsertos;
        this.totalComissao = totalComissao;
        this.salarioFixo = salarioFixo;
        this.salarioTotal = salarioTotal;
        this.quantidadeConsertos = quantidadeConsertos;
        this.comissaoGerais = comissaoGerais;
        this.comissaoPlaca = comissaoPlaca;
        this.comissaoLojistas = comissaoLojistas;
    }

    public BigDecimal getValorTotalConsertos() {
        return valorTotalConsertos;
    }

    public BigDecimal getTotalComissao() {
        return totalComissao;
    }

    public BigDecimal getSalarioFixo() {
        return salarioFixo;
    }

    public BigDecimal getSalarioTotal() {
        return salarioTotal;
    }

    public long getQuantidadeConsertos() {
        return quantidadeConsertos;
    }

    public BigDecimal getComissaoGerais() {
        return comissaoGerais;
    }

    public BigDecimal getComissaoPlaca() {
        return comissaoPlaca;
    }

    public BigDecimal getComissaoLojistas() {
        return comissaoLojistas;
    }
}
