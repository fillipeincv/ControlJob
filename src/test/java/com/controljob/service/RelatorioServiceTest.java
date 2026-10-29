package com.controljob.service;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;
import com.controljob.repository.ConsertoRepository;
import com.controljob.support.TestConsertos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    @Mock
    private ConsertoRepository repository;

    @Mock
    private SalarioConfigService salarioConfigService;

    private RelatorioService service;

    @BeforeEach
    void setUp() {
        service = new RelatorioService(repository, salarioConfigService);
    }

    @Test
    void calculaTotaisDoRelatorio() {
        List<Conserto> consertos = List.of(
                TestConsertos.retirado(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)),
                TestConsertos.retirado(2, TipoConserto.REPARO_DE_PLACA, "200.00",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))
        );
        when(repository.findAllByDataFechamentoIsNullAndDataRetiradaIsNotNullOrderByIdAsc()).thenReturn(consertos);
        when(repository.somarValorTotal()).thenReturn(new BigDecimal("300.00"));
        when(repository.somarValorPorTipo(TipoConserto.CONSERTOS_EM_GERAL)).thenReturn(new BigDecimal("100.00"));
        when(repository.somarValorPorTipo(TipoConserto.REPARO_DE_PLACA)).thenReturn(new BigDecimal("200.00"));
        when(repository.somarValorPorTipo(TipoConserto.CONSERTO_PARA_LOJISTAS)).thenReturn(BigDecimal.ZERO);
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));

        RelatorioService.RelatorioData dados = service.gerarRelatorio();

        assertThat(dados.consertos()).hasSize(2);
        assertThat(dados.quantidadeConsertos()).isEqualTo(2);
        assertThat(dados.valorTotalConsertos()).isEqualByComparingTo("300.00");
        assertThat(dados.comissaoGerais()).isEqualByComparingTo("5.00");
        assertThat(dados.comissaoPlaca()).isEqualByComparingTo("100.00");
        assertThat(dados.comissaoLojistas()).isEqualByComparingTo("0.00");
        assertThat(dados.totalComissao()).isEqualByComparingTo("105.00");
        assertThat(dados.salarioFixo()).isEqualByComparingTo("1000.00");
        assertThat(dados.salarioTotal()).isEqualByComparingTo("1105.00");
    }

    @Test
    void relatorioVazioMantemSalarioFixo() {
        when(repository.findAllByDataFechamentoIsNullAndDataRetiradaIsNotNullOrderByIdAsc()).thenReturn(List.of());
        when(repository.somarValorTotal()).thenReturn(BigDecimal.ZERO);
        when(repository.somarValorPorTipo(TipoConserto.CONSERTOS_EM_GERAL)).thenReturn(BigDecimal.ZERO);
        when(repository.somarValorPorTipo(TipoConserto.REPARO_DE_PLACA)).thenReturn(BigDecimal.ZERO);
        when(repository.somarValorPorTipo(TipoConserto.CONSERTO_PARA_LOJISTAS)).thenReturn(BigDecimal.ZERO);
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1500.00"));

        RelatorioService.RelatorioData dados = service.gerarRelatorio();

        assertThat(dados.consertos()).isEmpty();
        assertThat(dados.totalComissao()).isEqualByComparingTo("0.00");
        assertThat(dados.salarioTotal()).isEqualByComparingTo("1500.00");
    }
}
