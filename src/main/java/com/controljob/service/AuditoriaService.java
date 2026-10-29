package com.controljob.service;

import com.controljob.model.Agrupamento;
import com.controljob.model.Auditoria;
import com.controljob.model.Conserto;
import com.controljob.model.SituacaoConserto;
import com.controljob.repository.ConsertoRepository;
import com.controljob.repository.ConsertoSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AuditoriaService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SEPARADOR_CSV = ";";
    private static final String BOM = "﻿";

    private final ConsertoRepository repository;
    private final SalarioConfigService salarioConfigService;

    public AuditoriaService(
            ConsertoRepository repository,
            SalarioConfigService salarioConfigService
    ) {
        this.repository = repository;
        this.salarioConfigService = salarioConfigService;
    }

    @Transactional(readOnly = true)
    public Auditoria auditar(Auditoria.Criterios criterios, Agrupamento agrupamento) {
        List<Conserto> consertos = repository.findAll(montarFiltro(criterios));

        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal comissaoTotal = BigDecimal.ZERO;
        BigDecimal maior = null;
        BigDecimal menor = null;
        int aguardando = 0;

        for (Conserto c : consertos) {
            valorTotal = valorTotal.add(c.getValor());
            comissaoTotal = comissaoTotal.add(c.getComissao());
            if (c.getDataRetirada() == null) {
                aguardando++;
            }
            if (maior == null || c.getValor().compareTo(maior) > 0) {
                maior = c.getValor();
            }
            if (menor == null || c.getValor().compareTo(menor) < 0) {
                menor = c.getValor();
            }
        }

        int quantidade = consertos.size();

        return new Auditoria(
                criterios,
                consertos,
                escala(valorTotal),
                escala(comissaoTotal),
                media(valorTotal, quantidade),
                escala(maior),
                escala(menor),
                quantidade,
                aguardando,
                escala(salarioConfigService.salarioAtual().add(comissaoTotal)),
                agrupamento,
                agrupar(consertos, agrupamento),
                repository.listarModelos()
        );
    }

    public Specification<Conserto> montarFiltro(Auditoria.Criterios criterios) {
        Specification<Conserto> filtro = ConsertoSpecifications.noPeriodo(criterios.de(), criterios.ate());
        filtro = and(filtro, ConsertoSpecifications.dosTipos(criterios.tipos()));
        filtro = and(filtro, ConsertoSpecifications.doModelo(criterios.modelo()));
        filtro = and(filtro, ConsertoSpecifications.comTexto(criterios.texto()));
        filtro = and(filtro, ConsertoSpecifications.naFaixaValor(criterios.valorMinimo(), criterios.valorMaximo()));
        return and(filtro, ConsertoSpecifications.naSituacao(criterios.situacao()));
    }

    private Specification<Conserto> and(Specification<Conserto> base, Specification<Conserto> extra) {
        return extra == null ? base : (base == null ? extra : base.and(extra));
    }

    private List<Auditoria.Grupo> agrupar(List<Conserto> consertos, Agrupamento agrupamento) {
        if (agrupamento == null || agrupamento == Agrupamento.NENHUM || consertos.isEmpty()) {
            return List.of();
        }

        Map<String, Acumulado> grupos = new LinkedHashMap<>();

        for (Conserto c : consertos) {
            grupos.computeIfAbsent(chave(agrupamento, c), k -> new Acumulado()).add(c);
        }

        List<Map.Entry<String, Acumulado>> ordenados = new ArrayList<>(grupos.entrySet());
        ordenados.sort(agrupamento == Agrupamento.MES
                ? Comparator.comparing(Map.Entry::getKey)
                : Comparator.comparing((Map.Entry<String, Acumulado> e) -> e.getValue().quantidade)
                .reversed().thenComparing(Map.Entry::getKey));

        long maximo = ordenados.stream().mapToLong(e -> e.getValue().quantidade).max().orElse(0);

        return ordenados.stream()
                .map(e -> new Auditoria.Grupo(
                        e.getKey(),
                        maximo == 0 ? 0 : (int) Math.round(e.getValue().quantidade * 100.0 / maximo),
                        e.getValue().quantidade,
                        escala(e.getValue().valor),
                        escala(e.getValue().comissao)
                ))
                .toList();
    }

    private String chave(Agrupamento agrupamento, Conserto c) {
        return switch (agrupamento) {
            case MES -> rotularMes(YearMonth.from(c.getDataConserto()));
            case TIPO -> c.getTipoConserto().getLabel();
            case MODELO -> c.getModeloAparelho();
            case SITUACAO -> c.getDataRetirada() == null ? SituacaoConserto.AGUARDANDO_RETIRADA.getLabel() : SituacaoConserto.RETIRADO.getLabel();
            case NENHUM -> "—";
        };
    }

    private String rotularMes(YearMonth mes) {
        return mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR")) + " de " + mes.getYear();
    }

    public String gerarCsv(Auditoria auditoria) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append("Data").append(SEPARADOR_CSV)
                .append("Situação").append(SEPARADOR_CSV)
                .append("OS").append(SEPARADOR_CSV)
                .append("Modelo").append(SEPARADOR_CSV)
                .append("IMEI").append(SEPARADOR_CSV)
                .append("Tipo").append(SEPARADOR_CSV)
                .append("Descrição").append(SEPARADOR_CSV)
                .append("Valor").append(SEPARADOR_CSV)
                .append("Comissão").append(SEPARADOR_CSV)
                .append("Retirada").append(SEPARADOR_CSV)
                .append("Fechado em").append('\n');

        for (Conserto c : auditoria.consertos()) {
            csv.append(c.getDataConserto().format(DIA)).append(SEPARADOR_CSV)
                    .append(c.getDataRetirada() == null ? "Aguardando retirada" : "Retirado pelo cliente").append(SEPARADOR_CSV)
                    .append(campo(c.getNumeroOs())).append(SEPARADOR_CSV)
                    .append(campo(c.getModeloAparelho())).append(SEPARADOR_CSV)
                    .append(campo(c.getImei())).append(SEPARADOR_CSV)
                    .append(campo(c.getTipoConserto().getLabel())).append(SEPARADOR_CSV)
                    .append(campo(c.getDescricao())).append(SEPARADOR_CSV)
                .append(c.getValor().toPlainString()).append(SEPARADOR_CSV)
                .append(escala(c.getComissao()).toPlainString()).append(SEPARADOR_CSV)
                    .append(c.getDataRetirada() == null ? "" : c.getDataRetirada().format(DIA)).append(SEPARADOR_CSV)
                    .append(c.getDataFechamento() == null ? "" : c.getDataFechamento().format(DIA))
                    .append('\n');
        }

        csv.append('\n')
                .append(campo("Período")).append(SEPARADOR_CSV).append(campo(auditoria.criterios().rotuloPeriodo())).append('\n')
                .append(campo("Registros")).append(SEPARADOR_CSV).append(auditoria.quantidade()).append('\n')
                .append(campo("Valor total")).append(SEPARADOR_CSV).append(auditoria.valorTotal().toPlainString()).append('\n')
                .append(campo("Total de comissões")).append(SEPARADOR_CSV).append(auditoria.comissaoTotal().toPlainString()).append('\n')
                .append(campo("Salário total")).append(SEPARADOR_CSV).append(auditoria.salarioTotal().toPlainString()).append('\n');

        return csv.toString();
    }

    private String campo(String valor) {
        String limpo = neutralizarFormula(valor == null ? "" : valor);
        if (limpo.contains("\"") || limpo.contains(SEPARADOR_CSV) || limpo.contains("\n") || limpo.contains("\r")) {
            return '"' + limpo.replace("\"", "\"\"") + '"';
        }
        return limpo;
    }

    private String neutralizarFormula(String valor) {
        int inicio = 0;
        while (inicio < valor.length() && Character.isWhitespace(valor.charAt(inicio))) {
            inicio++;
        }
        if (inicio < valor.length() && "=+-@".indexOf(valor.charAt(inicio)) >= 0) {
            return "'" + valor;
        }
        return valor;
    }

    private BigDecimal media(BigDecimal total, int quantidade) {
        if (quantidade == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal escala(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Acumulado {
        private long quantidade;
        private BigDecimal valor = BigDecimal.ZERO;
        private BigDecimal comissao = BigDecimal.ZERO;

        void add(Conserto c) {
            quantidade++;
            valor = valor.add(c.getValor());
            comissao = comissao.add(c.getComissao());
        }
    }
}
