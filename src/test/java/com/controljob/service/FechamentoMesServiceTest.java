package com.controljob.service;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;
import com.controljob.repository.ConsertoRepository;
import com.controljob.support.TestConsertos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FechamentoMesServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO = Clock.fixed(
            ZonedDateTime.of(2026, 10, 31, 22, 0, 0, 0, FUSO).toInstant(), FUSO);

    @Mock
    private ConsertoRepository repository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SalarioConfigService salarioConfigService;

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private FechamentoMesService service;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        lenient().when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        service = new FechamentoMesService(
                repository,
                mailSender,
                objectMapper,
                salarioConfigService,
                tempDir.toString(),
                "remetente@example.com",
                "destino@example.com",
                "Relatório Mensal",
                RELOGIO
        );
    }

    @Test
    void semPendentesNaoGeraArquivosNemEnviaEmail() {
        when(repository.findByDataFechamentoIsNullAndDataRetiradaIsNotNullAndDataRetiradaLessThanEqual(any()))
                .thenReturn(List.of());

        FechamentoMesService.Resultado resultado = service.fecharMes();

        assertThat(resultado.quantidadeFechada()).isZero();
        assertThat(resultado.arquivoJson()).isNull();
        assertThat(resultado.arquivoPdf()).isNull();
        assertThat(resultado.emailEnviado()).isFalse();
        assertThat(resultado.erroEmail()).contains("Nenhum registro");
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(repository, never()).marcarFechados(anyList(), any());
    }

    @Test
    void fechaMesGeraArquivosEnviaEmailEMarcaConsertos() throws Exception {
        List<Conserto> consertos = consertosPendentes();
        when(repository.findByDataFechamentoIsNullAndDataRetiradaIsNotNullAndDataRetiradaLessThanEqual(any()))
                .thenReturn(consertos);
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));
        when(repository.marcarFechados(anyList(), any())).thenReturn(3);

        FechamentoMesService.Resultado resultado = service.fecharMes();

        assertThat(resultado.quantidadeFechada()).isEqualTo(3);
        assertThat(resultado.emailEnviado()).isTrue();
        assertThat(resultado.erroEmail()).isNull();
        assertThat(Path.of(resultado.arquivoJson())).exists();
        assertThat(Path.of(resultado.arquivoPdf())).exists();
        assertThat(Files.size(Path.of(resultado.arquivoPdf()))).isPositive();

        JsonNode json = objectMapper.readTree(Path.of(resultado.arquivoJson()).toFile());
        assertThat(json.get("quantidadeConsertos").asInt()).isEqualTo(3);
        assertThat(json.get("valorTotalConsertos").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(json.get("totalComissao").decimalValue()).isEqualByComparingTo("130.00");
        assertThat(json.get("salarioTotal").decimalValue()).isEqualByComparingTo("1130.00");
        assertThat(json.get("consertos").size()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<LocalDate> data = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).marcarFechados(ids.capture(), data.capture());
        assertThat(ids.getValue()).containsExactly(1L, 2L, 3L);
        assertThat(data.getValue()).isEqualTo(LocalDate.of(2026, 10, 31));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void falhaDeEmailNaoAbortaFechamento() {
        when(repository.findByDataFechamentoIsNullAndDataRetiradaIsNotNullAndDataRetiradaLessThanEqual(any()))
                .thenReturn(consertosPendentes());
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));
        when(repository.marcarFechados(anyList(), any())).thenReturn(3);
        doThrow(new MailSendException("smtp indisponível")).when(mailSender).send(any(MimeMessage.class));

        FechamentoMesService.Resultado resultado = service.fecharMes();

        assertThat(resultado.quantidadeFechada()).isEqualTo(3);
        assertThat(resultado.emailEnviado()).isFalse();
        assertThat(resultado.erroEmail()).contains("smtp indisponível");
        verify(repository).marcarFechados(anyList(), any());
    }

    @Test
    void falhaAoGerarArquivosNaoMarcaNemEnvia() throws Exception {
        when(repository.findByDataFechamentoIsNullAndDataRetiradaIsNotNullAndDataRetiradaLessThanEqual(any()))
                .thenReturn(consertosPendentes());
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));
        Path arquivoNoLugarDoDiretorio = tempDir.resolve("nao-e-diretorio");
        Files.writeString(arquivoNoLugarDoDiretorio, "x");
        FechamentoMesService comDiretorioInvalido = new FechamentoMesService(
                repository, mailSender, objectMapper, salarioConfigService,
                arquivoNoLugarDoDiretorio.toString(), "remetente@example.com",
                "destino@example.com", "Relatório Mensal", RELOGIO);

        assertThatThrownBy(comDiretorioInvalido::fecharMes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao gerar os arquivos");

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(repository, never()).marcarFechados(anyList(), any());
    }

    @Test
    void semDestinatarioRegistraErroMasFecha() {
        when(repository.findByDataFechamentoIsNullAndDataRetiradaIsNotNullAndDataRetiradaLessThanEqual(any()))
                .thenReturn(consertosPendentes());
        when(salarioConfigService.salarioAtual()).thenReturn(new BigDecimal("1000.00"));
        when(repository.marcarFechados(anyList(), any())).thenReturn(3);
        FechamentoMesService semDestinatario = new FechamentoMesService(
                repository, mailSender, objectMapper, salarioConfigService,
                tempDir.toString(), "remetente@example.com", "", "Relatório Mensal", RELOGIO);

        FechamentoMesService.Resultado resultado = semDestinatario.fecharMes();

        assertThat(resultado.quantidadeFechada()).isEqualTo(3);
        assertThat(resultado.emailEnviado()).isFalse();
        assertThat(resultado.erroEmail()).contains("Nenhum destinatário");
        verify(repository).marcarFechados(anyList(), any());
    }

    @Test
    void impedeDoisFechamentosSimultaneos() throws Exception {
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        when(repository.findByDataFechamentoIsNullAndDataRetiradaIsNotNullAndDataRetiradaLessThanEqual(any()))
                .thenAnswer(inv -> {
                    entrou.countDown();
                    if (!liberar.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timeout no teste");
                    }
                    return List.of();
                });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<FechamentoMesService.Resultado> primeira = executor.submit(service::fecharMes);
            assertThat(entrou.await(2, TimeUnit.SECONDS)).isTrue();

            FechamentoMesService.Resultado segunda = service.fecharMes();

            assertThat(segunda.quantidadeFechada()).isZero();
            assertThat(segunda.erroEmail()).contains("andamento");

            liberar.countDown();
            assertThat(primeira.get(2, TimeUnit.SECONDS).quantidadeFechada()).isZero();
        } finally {
            liberar.countDown();
            executor.shutdownNow();
        }
    }

    private List<Conserto> consertosPendentes() {
        return List.of(
                TestConsertos.retirado(1, TipoConserto.CONSERTOS_EM_GERAL, "100.00",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10)),
                TestConsertos.retirado(2, TipoConserto.REPARO_DE_PLACA, "200.00",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 11)),
                TestConsertos.retirado(3, TipoConserto.CONSERTO_PARA_LOJISTAS, "50.00",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 12))
        );
    }
}
