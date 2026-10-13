package com.controljob.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record Auditoria(
        Criterios criterios,
        List<Conserto> consertos,
        BigDecimal valorTotal,
        BigDecimal comissaoTotal,
        BigDecimal ticketMedio,
        BigDecimal maiorValor,
        BigDecimal menorValor,
        int quantidade,
        int quantidadeAguardando,
        BigDecimal salarioTotal,
        Agrupamento agrupamento,
        List<Grupo> grupos,
        List<String> modelosDisponiveis
) {

    public boolean vazio() {
        return quantidade == 0;
    }

    public record Criterios(
            LocalDate de,
            LocalDate ate,
            List<TipoConserto> tipos,
            String modelo,
            String texto,
            BigDecimal valorMinimo,
            BigDecimal valorMaximo,
            SituacaoConserto situacao
    ) {

        public static Criterios mesCorrente() {
            YearMonth mes = YearMonth.now();
            return new Criterios(mes.atDay(1), mes.atEndOfMonth(), List.of(), null, null, null, null, SituacaoConserto.TODOS);
        }

        public boolean temFiltroAplicado() {
            return (tipos != null && !tipos.isEmpty())
                    || modelo != null && !modelo.isBlank()
                    || texto != null && !texto.isBlank()
                    || valorMinimo != null
                    || valorMaximo != null
                    || situacao != null && situacao != SituacaoConserto.TODOS;
        }

        public String rotuloPeriodo() {
            if (de == null && ate == null) {
                return "Todo o histórico";
            }
            String inicio = de == null ? "…" : de.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String fim = ate == null ? "…" : ate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            return inicio + " a " + fim;
        }
    }

    public record Grupo(
            String rotulo,
            int pct,
            long quantidade,
            BigDecimal valor,
            BigDecimal comissao
    ) {
    }
}
