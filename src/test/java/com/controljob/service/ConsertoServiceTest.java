package com.controljob.service;

import com.controljob.exception.RecursoNaoEncontradoException;
import com.controljob.model.Conserto;
import com.controljob.model.DashboardResumo;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsertoServiceTest {

    @Mock
    private ConsertoRepository repository;

    @Mock
    private SalarioConfigService salarioConfigService;

    private ConsertoService service;

    @BeforeEach
    void setUp() {
        service = new ConsertoService(repository, salarioConfigService);
    }

    @Test
    void salvarNormalizaImei() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.salvar(novo(" 123 456 789 ")).getImei()).isEqualTo("123456789");
        assertThat(service.salvar(novo("   ")).getImei()).isNull();
        assertThat(service.salvar(novo(null)).getImei()).isNull();
    }

    @Test
    void salvarNovoIgnoraStatusEnviadoPeloFormulario() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Conserto conserto = novo("123");
        conserto.setDataRetirada(LocalDate.of(2026, 1, 1));
        conserto.setDataFechamento(LocalDate.of(2026, 1, 2));

        Conserto salvo = service.salvar(conserto);

        assertThat(salvo.getDataRetirada()).isNull();
        assertThat(salvo.getDataFechamento()).isNull();
    }

    @Test
    void salvarEdicaoPreservaRetiradaExistente() {
        LocalDate retirada = LocalDate.of(2026, 10, 10);
        Conserto existente = TestConsertos.retirado(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1), retirada);
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Conserto editado = novo("123");
        editado.setId(1L);

        Conserto salvo = service.salvar(editado);

        assertThat(salvo.getDataRetirada()).isEqualTo(retirada);
        assertThat(salvo.getDataFechamento()).isNull();
    }

    @Test
    void salvarEdicaoDeConsertoFechadoFalha() {
        Conserto existente = TestConsertos.retirado(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        existente.setDataFechamento(LocalDate.of(2026, 10, 31));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        Conserto editado = novo("123");
        editado.setId(1L);

        assertThatThrownBy(() -> service.salvar(editado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fechado");
        verify(repository, never()).save(any());
    }

    @Test
    void excluirConsertoFechadoFalha() {
        Conserto existente = TestConsertos.criar(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1));
        existente.setDataFechamento(LocalDate.of(2026, 10, 31));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.excluir(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("excluído");
        verify(repository, never()).delete(existente);
    }

    @Test
    void excluirConsertoAbertoRemove() {
        Conserto existente = TestConsertos.criar(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        service.excluir(1L);

        verify(repository).delete(existente);
    }

    @Test
    void buscarPorIdInexistenteLancaRecursoNaoEncontrado() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void registrarRetiradaGravaData() {
        Conserto existente = TestConsertos.criar(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.registrarRetirada(1L, LocalDate.of(2026, 10, 15));

        assertThat(existente.getDataRetirada()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void registrarRetiradaExigeData() {
        assertThatThrownBy(() -> service.registrarRetirada(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findById(any());
    }

    @Test
    void registrarRetiradaEmConsertoFechadoFalha() {
        Conserto existente = TestConsertos.retirado(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        existente.setDataFechamento(LocalDate.of(2026, 10, 31));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.registrarRetirada(1L, LocalDate.of(2026, 10, 15)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void desfazerRetiradaLimpaData() {
        Conserto existente = TestConsertos.retirado(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.desfazerRetirada(1L);

        assertThat(existente.getDataRetirada()).isNull();
    }

    @Test
    void historicoPorImeiNormalizaEntradaEIgnoraVazio() {
        when(repository.findByImeiIgnoreCaseOrderByDataConsertoDesc("123456"))
                .thenReturn(List.of());

        assertThat(service.historicoPorImei(" 123 456 ")).isEmpty();
        assertThat(service.historicoPorImei("   ")).isEmpty();
        assertThat(service.historicoPorImei(null)).isEmpty();

        verify(repository).findByImeiIgnoreCaseOrderByDataConsertoDesc("123456");
    }

    @Test
    void resumoContaSomenteConsertosRetirados() {
        List<Conserto> consertos = List.of(
                TestConsertos.retirado(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)),
                TestConsertos.retirado(2, TipoConserto.REPARO_DE_PLACA, "200.00",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3)),
                TestConsertos.retirado(3, TipoConserto.CONSERTO_PARA_LOJISTAS, "50.00",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4)),
                TestConsertos.criar(4, TipoConserto.CONSERTOS_EM_GERAL, "999.00",
                        LocalDate.of(2026, 10, 5))
        );
        when(repository.findAllByDataFechamentoIsNullOrderByDataConsertoDesc()).thenReturn(consertos);
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));

        DashboardResumo resumo = service.gerarResumo();

        assertThat(resumo.getQuantidadeConsertos()).isEqualTo(3);
        assertThat(resumo.getQuantidadeAguardando()).isEqualTo(1);
        assertThat(resumo.getValorTotalConsertos()).isEqualByComparingTo("350.00");
        assertThat(resumo.getComissaoGerais()).isEqualByComparingTo("5.00");
        assertThat(resumo.getComissaoPlaca()).isEqualByComparingTo("100.00");
        assertThat(resumo.getComissaoLojistas()).isEqualByComparingTo("25.00");
        assertThat(resumo.getTotalComissao()).isEqualByComparingTo("130.00");
        assertThat(resumo.getSalarioTotal()).isEqualByComparingTo("1130.00");
    }

    @Test
    void resumoSemConsertosZeraValores() {
        when(repository.findAllByDataFechamentoIsNullOrderByDataConsertoDesc()).thenReturn(List.of());
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1500.00"));

        DashboardResumo resumo = service.gerarResumo();

        assertThat(resumo.getQuantidadeConsertos()).isZero();
        assertThat(resumo.getQuantidadeAguardando()).isZero();
        assertThat(resumo.getValorTotalConsertos()).isEqualByComparingTo("0.00");
        assertThat(resumo.getSalarioTotal()).isEqualByComparingTo("1500.00");
    }

    private Conserto novo(String imei) {
        Conserto conserto = TestConsertos.criar(0, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1));
        conserto.setId(null);
        conserto.setImei(imei);
        return conserto;
    }
}
