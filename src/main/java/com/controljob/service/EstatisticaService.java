package com.controljob.service;

import com.controljob.model.Conserto;
import com.controljob.model.PainelAnalitico;
import com.controljob.model.TipoConserto;
import com.controljob.repository.ConsertoRepository;
import com.controljob.repository.ConsertoSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
public class EstatisticaService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TOP_MODELOS = 10;
    private static final double CIRCUNFERENCIA = 2 * Math.PI * 54;

    private final ConsertoRepository repository;

    public EstatisticaService(ConsertoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PainelAnalitico gerarPainel(LocalDate de, LocalDate ate) {
        List<Conserto> consertos = repository.findAll(ConsertoSpecifications.noPeriodo(de, ate));

        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal comissaoTotal = BigDecimal.ZERO;
        int fechados = 0;

        Map<TipoConserto, BigDecimal> valorPorTipo = new LinkedHashMap<>();
        Map<TipoConserto, Long> qtdPorTipo = new LinkedHashMap<>();
        Map<String, Long> qtdPorModelo = new LinkedHashMap<>();
        Map<YearMonth, List<Conserto>> porMes = new LinkedHashMap<>();

        for (Conserto c : consertos) {
            valorTotal = valorTotal.add(c.getValor());
            comissaoTotal = comissaoTotal.add(c.getComissao());
            if (c.getDataFechamento() != null) {
                fechados++;
            }

            TipoConserto tipo = c.getTipoConserto();
            valorPorTipo.merge(tipo, c.getValor(), BigDecimal::add);
            qtdPorTipo.merge(tipo, 1L, Long::sum);
            qtdPorModelo.merge(c.getModeloAparelho(), 1L, Long::sum);
            porMes.computeIfAbsent(YearMonth.from(c.getDataConserto()), k -> new ArrayList<>()).add(c);
        }

        int total = consertos.size();
        int pendentes = total - fechados;

        return new PainelAnalitico(
                rotuloPeriodo(de, ate),
                total,
                escala(valorTotal),
                escala(comissaoTotal),
                media(valorTotal, total),
                fechados,
                pendentes,
                total == 0 ? BigDecimal.ZERO : escala(BigDecimal.valueOf(total).divide(BigDecimal.valueOf(mesesEntre(de, ate)), 2, RoundingMode.HALF_UP)),
                evolucaoMensal(de, ate, porMes),
                distribuicaoTipo(qtdPorTipo, valorPorTipo, total),
                topModelos(consertos, qtdPorModelo, total),
                recorrencias(consertos, total),
                contarRecorrentes(consertos),
                contarComImei(consertos),
                valorRecorrente(consertos),
                percentualRecorrente(consertos, total),
                contarSemImei(consertos)
        );
    }

    private List<PainelAnalitico.BarraMensal> evolucaoMensal(
            LocalDate de, LocalDate ate, Map<YearMonth, List<Conserto>> porMes) {

        YearMonth inicio = YearMonth.from(de != null ? de : LocalDate.now().withDayOfMonth(1));
        YearMonth fim = YearMonth.from(ate != null ? ate : LocalDate.now());

        Map<YearMonth, AcumuladoMes> acumulado = new LinkedHashMap<>();
        YearMonth cursor = inicio;
        while (!cursor.isAfter(fim)) {
            List<Conserto> doMes = porMes.getOrDefault(cursor, List.of());
            BigDecimal valor = BigDecimal.ZERO;
            BigDecimal comissao = BigDecimal.ZERO;
            for (Conserto c : doMes) {
                valor = valor.add(c.getValor());
                comissao = comissao.add(c.getComissao());
            }
            acumulado.put(cursor, new AcumuladoMes(doMes.size(), escala(valor), escala(comissao)));
            cursor = cursor.plusMonths(1);
        }

        long maxQuantidade = acumulado.values().stream().mapToLong(AcumuladoMes::quantidade).max().orElse(0);
        BigDecimal maxValor = acumulado.values().stream().map(AcumuladoMes::valor)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        List<PainelAnalitico.BarraMensal> barras = new ArrayList<>();
        for (Map.Entry<YearMonth, AcumuladoMes> entry : acumulado.entrySet()) {
            YearMonth mes = entry.getKey();
            AcumuladoMes dados = entry.getValue();

            barras.add(new PainelAnalitico.BarraMensal(
                    mes.getMonth().getDisplayName(TextStyle.SHORT, new Locale("pt", "BR")).replace(".", "")
                            + "/" + String.format("%02d", mes.getYear() % 100),
                    mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR")) + " de " + mes.getYear(),
                    maxQuantidade == 0 ? 0 : (int) Math.round(dados.quantidade() * 100.0 / maxQuantidade),
                    maxValor.signum() == 0 ? 0 : dados.valor().multiply(BigDecimal.valueOf(100))
                            .divide(maxValor, 0, RoundingMode.HALF_UP).intValue(),
                    dados.quantidade(),
                    dados.valor(),
                    dados.comissao(),
                    media(dados.valor(), (int) dados.quantidade())
            ));
        }
        return barras;
    }

    private record AcumuladoMes(long quantidade, BigDecimal valor, BigDecimal comissao) {
    }

    private List<PainelAnalitico.FatiaTipo> distribuicaoTipo(
            Map<TipoConserto, Long> qtdPorTipo, Map<TipoConserto, BigDecimal> valorPorTipo, int total) {

        List<PainelAnalitico.FatiaTipo> fatias = new ArrayList<>();
        double offset = 0;

        for (TipoConserto tipo : TipoConserto.values()) {
            long quantidade = qtdPorTipo.getOrDefault(tipo, 0L);
            double fracao = total == 0 ? 0 : quantidade / (double) total;
            double length = fracao * CIRCUNFERENCIA;

            fatias.add(new PainelAnalitico.FatiaTipo(
                    tipo.getLabel(),
                    "tag-" + tipo.name(),
                    (int) Math.round(fracao * 100),
                    numero(length),
                    numero(CIRCUNFERENCIA - length),
                    numero(-offset),
                    quantidade,
                    escala(valorPorTipo.getOrDefault(tipo, BigDecimal.ZERO)),
                    escala(valorPorTipo.getOrDefault(tipo, BigDecimal.ZERO).multiply(tipo.getPercentualComissao()))
            ));
            offset += length;
        }
        return fatias;
    }

    private List<PainelAnalitico.BarraModelo> topModelos(
            List<Conserto> consertos, Map<String, Long> qtdPorModelo, int total) {

        Map<String, BigDecimal> valorPorModelo = new LinkedHashMap<>();
        for (Conserto c : consertos) {
            valorPorModelo.merge(c.getModeloAparelho(), c.getValor(), BigDecimal::add);
        }

        long maximo = qtdPorModelo.values().stream().mapToLong(Long::longValue).max().orElse(0);

        return qtdPorModelo.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_MODELOS)
                .map(e -> new PainelAnalitico.BarraModelo(
                        e.getKey(),
                        maximo == 0 ? 0 : (int) Math.round(e.getValue() * 100.0 / maximo),
                        e.getValue(),
                        escala(valorPorModelo.getOrDefault(e.getKey(), BigDecimal.ZERO))
                ))
                .toList();
    }

    private List<PainelAnalitico.Recorrencia> recorrencias(List<Conserto> consertos, int total) {

        Map<String, List<Conserto>> porImei = new LinkedHashMap<>();
        for (Conserto c : consertos) {
            String imei = c.getImei();
            if (imei != null && !imei.isBlank()) {
                porImei.computeIfAbsent(imei.trim(), k -> new ArrayList<>()).add(c);
            }
        }

        List<PainelAnalitico.Recorrencia> repetidos = new ArrayList<>();
        for (Map.Entry<String, List<Conserto>> entry : porImei.entrySet()) {
            List<Conserto> doImei = entry.getValue();
            if (doImei.size() < 2) {
                continue;
            }

            List<Conserto> ordenados = doImei.stream()
                    .sorted(Comparator.comparing(Conserto::getDataConserto))
                    .toList();

            BigDecimal valorTotal = BigDecimal.ZERO;
            for (Conserto c : ordenados) {
                valorTotal = valorTotal.add(c.getValor());
            }

            LocalDate primeira = ordenados.get(0).getDataConserto();
            LocalDate ultima = ordenados.get(ordenados.size() - 1).getDataConserto();
            String modelo = ordenados.stream()
                    .map(Conserto::getModeloAparelho)
                    .max(Comparator.comparing(m -> contagem(ordenados, m)))
                    .orElse("—");

            repetidos.add(new PainelAnalitico.Recorrencia(
                    entry.getKey(),
                    modelo,
                    ordenados.size(),
                    escala(valorTotal),
                    java.time.temporal.ChronoUnit.DAYS.between(primeira, ultima),
                    primeira.format(DIA),
                    ultima.format(DIA),
                    total == 0 ? 0 : (int) Math.round(ordenados.size() * 100.0 / total)
            ));
        }

        long maximo = repetidos.stream().mapToLong(PainelAnalitico.Recorrencia::quantidade).max().orElse(0);

        return repetidos.stream()
                .sorted(Comparator.comparingLong(PainelAnalitico.Recorrencia::quantidade).reversed()
                        .thenComparing(PainelAnalitico.Recorrencia::valorTotal, Comparator.reverseOrder()))
                .limit(TOP_MODELOS)
                .map(r -> new PainelAnalitico.Recorrencia(
                        r.imei(), r.modelo(), r.quantidade(), r.valorTotal(), r.intervaloDias(),
                        r.primeiraData(), r.ultimaData(),
                        maximo == 0 ? 0 : (int) Math.round(r.quantidade() * 100.0 / maximo)
                ))
                .toList();
    }

    private long contagem(List<Conserto> consertos, String modelo) {
        return consertos.stream().filter(c -> c.getModeloAparelho().equals(modelo)).count();
    }

    private int contarRecorrentes(List<Conserto> consertos) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (Conserto c : consertos) {
            if (c.getImei() != null && !c.getImei().isBlank()) {
                contagem.merge(c.getImei().trim(), 1, Integer::sum);
            }
        }
        return (int) contagem.values().stream().filter(v -> v >= 2).count();
    }

    private int contarComImei(List<Conserto> consertos) {
        return (int) consertos.stream().filter(c -> c.getImei() != null && !c.getImei().isBlank()).count();
    }

    private int contarSemImei(List<Conserto> consertos) {
        return consertos.size() - contarComImei(consertos);
    }

    private BigDecimal valorRecorrente(List<Conserto> consertos) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (Conserto c : consertos) {
            if (c.getImei() != null && !c.getImei().isBlank()) {
                contagem.merge(c.getImei().trim(), 1, Integer::sum);
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Conserto c : consertos) {
            if (c.getImei() != null && contagem.getOrDefault(c.getImei().trim(), 0) >= 2) {
                total = total.add(c.getValor());
            }
        }
        return escala(total);
    }

    private BigDecimal percentualRecorrente(List<Conserto> consertos, int total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (Conserto c : consertos) {
            if (c.getImei() != null && !c.getImei().isBlank()) {
                contagem.merge(c.getImei().trim(), 1, Integer::sum);
            }
        }
        long recurrentes = consertos.stream()
                .filter(c -> c.getImei() != null && contagem.getOrDefault(c.getImei().trim(), 0) >= 2)
                .count();
        return BigDecimal.valueOf(recurrentes * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    private long mesesEntre(LocalDate de, LocalDate ate) {
        YearMonth inicio = YearMonth.from(de != null ? de : LocalDate.now().withDayOfMonth(1));
        YearMonth fim = YearMonth.from(ate != null ? ate : LocalDate.now());
        return Math.max(1, java.time.temporal.ChronoUnit.MONTHS.between(inicio, fim) + 1);
    }

    private String rotuloPeriodo(LocalDate de, LocalDate ate) {
        if (de == null && ate == null) {
            return "Todo o histórico";
        }
        if (de == null) {
            return "Até " + ate.format(DIA);
        }
        if (ate == null) {
            return "A partir de " + de.format(DIA);
        }
        if (de.equals(ate)) {
            return de.format(DIA);
        }
        return de.format(DIA) + " a " + ate.format(DIA);
    }

    private BigDecimal media(BigDecimal total, int quantidade) {
        if (quantidade == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String numero(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
