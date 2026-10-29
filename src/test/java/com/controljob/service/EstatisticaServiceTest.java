package com.controljob.service;

import com.controljob.model.Conserto;
import com.controljob.model.PainelAnalitico;
import com.controljob.model.TipoConserto;
import com.controljob.repository.ConsertoRepository;
import com.controljob.support.TestConsertos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstatisticaServiceTest {

    @Mock
    private ConsertoRepository repository;

    private EstatisticaService service;

    @BeforeEach
    void setUp() {
        service = new EstatisticaService(repository);
    }

    @Test
    void calculaIndicadoresSomenteComConsertosRetirados() {
        when(repository.findAll(any(Specification.class))).thenReturn(consertos());

        PainelAnalitico painel = service.gerarPainel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));

        assertThat(painel.periodoRotulo()).isEqualTo("01/09/2026 a 31/10/2026");
        assertThat(painel.totalConsertos()).isEqualTo(5);
        assertThat(painel.valorTotal()).isEqualByComparingTo("490.00");
        assertThat(painel.comissaoTotal()).isEqualByComparingTo("182.00");
        assertThat(painel.ticketMedio()).isEqualByComparingTo("122.50");
        assertThat(painel.totalFechados()).isEqualTo(1);
        assertThat(painel.totalPendentes()).isEqualTo(4);
        assertThat(painel.mediaMensalConsertos()).isEqualByComparingTo("2.50");
    }

    @Test
    void montaEvolucaoMensalDoPeriodo() {
        when(repository.findAll(any(Specification.class))).thenReturn(consertos());

        PainelAnalitico painel = service.gerarPainel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));

        assertThat(painel.evolucaoMensal()).hasSize(2);

        PainelAnalitico.BarraMensal setembro = painel.evolucaoMensal().get(0);
        assertThat(setembro.quantidade()).isEqualTo(2);
        assertThat(setembro.valor()).isEqualByComparingTo("100.00");
        assertThat(setembro.comissao()).isEqualByComparingTo("5.00");

        PainelAnalitico.BarraMensal outubro = painel.evolucaoMensal().get(1);
        assertThat(outubro.quantidade()).isEqualTo(3);
        assertThat(outubro.valor()).isEqualByComparingTo("390.00");
        assertThat(outubro.comissao()).isEqualByComparingTo("177.00");
    }

    @Test
    void distribuiPorTipoConsiderandoQuantidadeEValorRetirado() {
        when(repository.findAll(any(Specification.class))).thenReturn(consertos());

        PainelAnalitico painel = service.gerarPainel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));

        assertThat(painel.distribuicaoTipo()).hasSize(3);

        PainelAnalitico.FatiaTipo geral = porTipo(painel, "Consertos em Geral");
        assertThat(geral.quantidade()).isEqualTo(2);
        assertThat(geral.valor()).isEqualByComparingTo("140.00");
        assertThat(geral.comissao()).isEqualByComparingTo("7.00");

        PainelAnalitico.FatiaTipo placa = porTipo(painel, "Reparo de Placa");
        assertThat(placa.quantidade()).isEqualTo(2);
        assertThat(placa.valor()).isEqualByComparingTo("300.00");
        assertThat(placa.comissao()).isEqualByComparingTo("150.00");
    }

    @Test
    void identificaRecorrenciasPorImei() {
        when(repository.findAll(any(Specification.class))).thenReturn(consertos());

        PainelAnalitico painel = service.gerarPainel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));

        assertThat(painel.recorrencias()).hasSize(1);
        PainelAnalitico.Recorrencia recorrencia = painel.recorrencias().get(0);
        assertThat(recorrencia.imei()).isEqualTo("111111111111111");
        assertThat(recorrencia.quantidade()).isEqualTo(2);
        assertThat(recorrencia.valorTotal()).isEqualByComparingTo("300.00");
        assertThat(recorrencia.intervaloDias()).isEqualTo(15);
        assertThat(painel.totalRecorrentes()).isEqualTo(1);
        assertThat(painel.quantosImei()).isEqualTo(4);
        assertThat(painel.valorRecorrente()).isEqualByComparingTo("300.00");
        assertThat(painel.percentualRecorrente()).isEqualByComparingTo("40.0");
        assertThat(painel.totalSemImei()).isEqualTo(1);
    }

    @Test
    void periodoVazioNaoQuebra() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());

        PainelAnalitico painel = service.gerarPainel(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(painel.totalConsertos()).isZero();
        assertThat(painel.valorTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(painel.ticketMedio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(painel.evolucaoMensal()).hasSize(1);
        assertThat(painel.topModelos()).isEmpty();
        assertThat(painel.recorrencias()).isEmpty();
    }

    private PainelAnalitico.FatiaTipo porTipo(PainelAnalitico painel, String rotulo) {
        return painel.distribuicaoTipo().stream()
                .filter(fatia -> fatia.rotulo().equals(rotulo))
                .findFirst()
                .orElseThrow();
    }

    private List<Conserto> consertos() {
        Conserto c1 = TestConsertos.retirado(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 10));
        c1.setImei("111111111111111");
        c1.setDataFechamento(LocalDate.of(2026, 9, 30));

        Conserto c2 = TestConsertos.criar(2, TipoConserto.REPARO_DE_PLACA, "200.00",
                LocalDate.of(2026, 9, 20));
        c2.setImei("111111111111111");

        Conserto c3 = TestConsertos.retirado(3, TipoConserto.REPARO_DE_PLACA, "300.00",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6));
        c3.setImei("222222222222222");

        Conserto c4 = TestConsertos.retirado(4, TipoConserto.CONSERTO_PARA_LOJISTAS, "50.00",
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11));
        c4.setImei("333333333333333");

        Conserto c5 = TestConsertos.retirado(5, TipoConserto.CONSERTOS_EM_GERAL, "40.00",
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21));
        c5.setImei(null);

        return List.of(c1, c2, c3, c4, c5);
    }
}
