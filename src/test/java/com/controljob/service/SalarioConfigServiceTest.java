package com.controljob.service;

import com.controljob.model.SalarioConfig;
import com.controljob.repository.SalarioConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalarioConfigServiceTest {

    @Mock
    private SalarioConfigRepository repository;

    private SalarioConfigService service;

    @BeforeEach
    void setUp() {
        service = new SalarioConfigService(repository, new BigDecimal("1500.00"));
    }

    @Test
    void retornaValorPersistido() {
        when(repository.findById(SalarioConfig.ID_UNICO)).thenReturn(Optional.of(config("2500.00")));

        assertThat(service.salarioAtual()).isEqualByComparingTo("2500.00");
    }

    @Test
    void inicializaComValorPadraoQuandoNaoExiste() {
        when(repository.findById(SalarioConfig.ID_UNICO)).thenReturn(Optional.empty());

        BigDecimal valor = service.salarioAtual();

        assertThat(valor).isEqualByComparingTo("1500.00");
        ArgumentCaptor<SalarioConfig> captor = ArgumentCaptor.forClass(SalarioConfig.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(SalarioConfig.ID_UNICO);
        assertThat(captor.getValue().getValor()).isEqualByComparingTo("1500.00");
    }

    @Test
    void atualizaValorExistenteComEscalaDeDoisDecimais() {
        when(repository.findById(SalarioConfig.ID_UNICO)).thenReturn(Optional.of(config("1000.00")));

        BigDecimal valor = service.atualizar(new BigDecimal("2000.005"));

        assertThat(valor).isEqualByComparingTo("2000.01");
        ArgumentCaptor<SalarioConfig> captor = ArgumentCaptor.forClass(SalarioConfig.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValor()).isEqualByComparingTo("2000.01");
    }

    @Test
    void criaConfiguracaoAoAtualizarQuandoNaoExiste() {
        when(repository.findById(SalarioConfig.ID_UNICO)).thenReturn(Optional.empty());

        service.atualizar(new BigDecimal("1800.00"));

        ArgumentCaptor<SalarioConfig> captor = ArgumentCaptor.forClass(SalarioConfig.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValor()).isEqualByComparingTo("1800.00");
    }

    @Test
    void rejeitaValoresNaoPositivos() {
        assertThatThrownBy(() -> service.atualizar(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.atualizar(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.atualizar(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    private SalarioConfig config(String valor) {
        SalarioConfig config = new SalarioConfig();
        config.setValor(new BigDecimal(valor));
        return config;
    }
}
