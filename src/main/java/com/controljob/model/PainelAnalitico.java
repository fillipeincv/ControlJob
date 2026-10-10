package com.controljob.model;

import java.math.BigDecimal;
import java.util.List;

public record PainelAnalitico(
        String periodoRotulo,
        int totalConsertos,
        BigDecimal valorTotal,
        BigDecimal comissaoTotal,
        BigDecimal ticketMedio,
        int totalFechados,
        int totalPendentes,
        BigDecimal mediaMensalConsertos,
        List<BarraMensal> evolucaoMensal,
        List<FatiaTipo> distribuicaoTipo,
        List<BarraModelo> topModelos,
        List<Recorrencia> recorrencias,
        int totalRecorrentes,
        int quantosImei,
        BigDecimal valorRecorrente,
        BigDecimal percentualRecorrente,
        int totalSemImei
) {

    public boolean vazio() {
        return totalConsertos == 0;
    }

    public record BarraMensal(
            String rotulo,
            String rotuloLongo,
            int pctQuantidade,
            int pctValor,
            long quantidade,
            BigDecimal valor,
            BigDecimal comissao,
            BigDecimal ticketMedio
    ) {
    }

    public record FatiaTipo(
            String rotulo,
            String classe,
            int pct,
            String dashLength,
            String dashGap,
            String dashOffset,
            long quantidade,
            BigDecimal valor,
            BigDecimal comissao
    ) {
    }

    public record BarraModelo(
            String rotulo,
            int pct,
            long quantidade,
            BigDecimal valor
    ) {
    }

    public record Recorrencia(
            String imei,
            String modelo,
            long quantidade,
            BigDecimal valorTotal,
            long intervaloDias,
            String primeiraData,
            String ultimaData,
            int pct
    ) {
    }
}
