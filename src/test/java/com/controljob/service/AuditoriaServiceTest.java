package com.controljob.service;

import com.controljob.model.Agrupamento;
import com.controljob.model.Auditoria;
import com.controljob.model.Conserto;
import com.controljob.model.SituacaoConserto;
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
class AuditoriaServiceTest {

    @Mock
    private ConsertoRepository repository;

    @Mock
    private SalarioConfigService salarioConfigService;

    private AuditoriaService service;

    @BeforeEach
    void setUp() {
        service = new AuditoriaService(repository, salarioConfigService);
    }

    @Test
    void calculaTotaisDaAuditoria() {
        when(repository.findAll(any(Specification.class))).thenReturn(consertos());
        when(repository.listarModelos()).thenReturn(List.of("Modelo 1", "Modelo 2", "Modelo 3"));
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));

        Auditoria auditoria = service.auditar(criterios(), Agrupamento.NENHUM);

        assertThat(auditoria.quantidade()).isEqualTo(3);
        assertThat(auditoria.quantidadeAguardando()).isEqualTo(1);
        assertThat(auditoria.valorTotal()).isEqualByComparingTo("350.00");
        assertThat(auditoria.comissaoTotal()).isEqualByComparingTo("130.00");
        assertThat(auditoria.ticketMedio()).isEqualByComparingTo("116.67");
        assertThat(auditoria.maiorValor()).isEqualByComparingTo("200.00");
        assertThat(auditoria.menorValor()).isEqualByComparingTo("50.00");
        assertThat(auditoria.salarioTotal()).isEqualByComparingTo("1130.00");
        assertThat(auditoria.grupos()).isEmpty();
        assertThat(auditoria.modelosDisponiveis()).hasSize(3);
    }

    @Test
    void agrupaPorTipoESituacao() {
        when(repository.findAll(any(Specification.class))).thenReturn(consertos());
        when(repository.listarModelos()).thenReturn(List.of());
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));

        Auditoria porTipo = service.auditar(criterios(), Agrupamento.TIPO);
        assertThat(porTipo.grupos()).hasSize(3);
        assertThat(porTipo.grupos()).extracting(Auditoria.Grupo::rotulo)
                .containsExactlyInAnyOrder("Consertos em Geral", "Reparo de Placa", "Conserto para Lojistas");

        Auditoria porSituacao = service.auditar(criterios(), Agrupamento.SITUACAO);
        assertThat(porSituacao.grupos()).extracting(Auditoria.Grupo::rotulo)
                .containsExactlyInAnyOrder("Aguardando retirada", "Retirado pelo cliente");
    }

    @Test
    void csvContemTotaisENeutralizaFormulas() {
        List<Conserto> consertos = consertos();
        consertos.get(0).setNumeroOs("=1+1");
        consertos.get(0).setDescricao("-cmd|calc");
        consertos.get(1).setDescricao("texto;com;separador");
        when(repository.findAll(any(Specification.class))).thenReturn(consertos);
        when(repository.listarModelos()).thenReturn(List.of());
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));
        Auditoria auditoria = service.auditar(criterios(), Agrupamento.NENHUM);

        String csv = service.gerarCsv(auditoria);

        assertThat(csv).startsWith("\uFEFFData;Situação;OS;Modelo;IMEI;Tipo;Descrição;Valor;Comissão;Retirada;Fechado em");
        assertThat(csv).contains("'=1+1");
        assertThat(csv).contains("'-cmd|calc");
        assertThat(csv).contains("\"texto;com;separador\"");
        assertThat(csv).contains("Registros;3");
        assertThat(csv).contains("Valor total;350.00");
        assertThat(csv).contains("Total de comissões;130.00");
        assertThat(csv).contains("Salário total;1130.00");
    }

    private Auditoria.Criterios criterios() {
        return new Auditoria.Criterios(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                List.of(),
                null,
                null,
                null,
                null,
                SituacaoConserto.TODOS
        );
    }

    private List<Conserto> consertos() {
        Conserto aguardando = TestConsertos.criar(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                LocalDate.of(2026, 10, 1));
        Conserto placa = TestConsertos.retirado(2, TipoConserto.REPARO_DE_PLACA, "200.00",
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3));
        Conserto lojistas = TestConsertos.retirado(3, TipoConserto.CONSERTO_PARA_LOJISTAS, "50.00",
                LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5));
        return new java.util.ArrayList<>(List.of(aguardando, placa, lojistas));
    }
}
