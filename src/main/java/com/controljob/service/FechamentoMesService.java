package com.controljob.service;

import com.controljob.model.Conserto;
import com.controljob.repository.ConsertoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FechamentoMesService {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final ConsertoRepository repository;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final BigDecimal salarioFixo;
    private final String diretorioAuditoria;
    private final String emailRemetente;
    private final List<String> emailDestinatarios;
    private final String emailAssunto;

    public FechamentoMesService(
            ConsertoRepository repository,
            JavaMailSender mailSender,
            ObjectMapper objectMapper,
            @Value("${controljob.salario-fixo:1500.00}") BigDecimal salarioFixo,
            @Value("${controljob.auditoria.diretorio:./data/auditoria}") String diretorioAuditoria,
            @Value("${controljob.email.remetente:}") String emailRemetente,
            @Value("${controljob.email.destinatario:}") String emailDestinatario,
            @Value("${controljob.email.assunto:Relatório Mensal de Pagamentos}") String emailAssunto
    ) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.salarioFixo = salarioFixo;
        this.diretorioAuditoria = diretorioAuditoria;
        this.emailRemetente = emailRemetente;
        this.emailDestinatarios = Arrays.stream(emailDestinatario.split(","))
                .map(String::trim)
                .filter(destinatario -> !destinatario.isEmpty())
                .toList();
        this.emailAssunto = emailAssunto;
    }

    @Transactional
    public Resultado fecharMes() {
        ZonedDateTime agora = ZonedDateTime.now(ZONE_ID);
        YearMonth mes = YearMonth.now(ZONE_ID);
        LocalDate ultimoDia = mes.atEndOfMonth();
        LocalDate hoje = agora.toLocalDate();

        List<Conserto> consertos = repository.findByDataFechamentoIsNullAndDataConsertoLessThanEqual(ultimoDia);
        if (consertos.isEmpty()) {
            return new Resultado(0, null, null, false, "Nenhum registro pendente de fechamento.");
        }

        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal comissaoGerais = BigDecimal.ZERO;
        BigDecimal comissaoPlaca = BigDecimal.ZERO;
        BigDecimal comissaoLojistas = BigDecimal.ZERO;
        List<Long> ids = new ArrayList<>();
        for (Conserto c : consertos) {
            ids.add(c.getId());
            valorTotal = valorTotal.add(c.getValor());
            BigDecimal comissao = c.getComissao();
            switch (c.getTipoConserto()) {
                case CONSERTOS_EM_GERAL -> comissaoGerais = comissaoGerais.add(comissao);
                case REPARO_DE_PLACA -> comissaoPlaca = comissaoPlaca.add(comissao);
                case CONSERTO_PARA_LOJISTAS -> comissaoLojistas = comissaoLojistas.add(comissao);
            }
        }
        BigDecimal totalComissao = comissaoGerais.add(comissaoPlaca).add(comissaoLojistas);
        BigDecimal salarioTotal = salarioFixo.add(totalComissao);

        String nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
        String mesLabel = nomeMes.substring(0, 1).toUpperCase(new Locale("pt", "BR"))
                + nomeMes.substring(1) + " de " + mes.getYear();

        String erroEmail = null;
        File arquivoJson = null;
        File arquivoPdf = null;
        try {
            Files.createDirectories(Path.of(diretorioAuditoria));
            arquivoJson = Path.of(diretorioAuditoria, "fechamento-" + mes + ".json").toFile();
            arquivoPdf = Path.of(diretorioAuditoria, "relatorio-" + mes + ".pdf").toFile();

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(arquivoJson, montarAuditoria(
                    agora, mesLabel, consertos, valorTotal, comissaoGerais, comissaoPlaca,
                    comissaoLojistas, totalComissao, salarioTotal));

            gerarPdf(arquivoPdf, mesLabel, agora, consertos, valorTotal, totalComissao, salarioTotal);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar os arquivos de auditoria do mês: " + e.getMessage(), e);
        }

        try {
            enviarEmail(mesLabel, consertos.size(), valorTotal, totalComissao, salarioTotal, arquivoJson, arquivoPdf);
        } catch (Exception e) {
            erroEmail = "Falha ao enviar e-mail: " + e.getMessage();
        }

        int marcados = repository.marcarFechados(ids, hoje);

        return new Resultado(marcados, arquivoJson.getAbsolutePath(), arquivoPdf.getAbsolutePath(),
                erroEmail == null, erroEmail);
    }

    private Map<String, Object> montarAuditoria(
            ZonedDateTime agora, String mesLabel, List<Conserto> consertos,
            BigDecimal valorTotal, BigDecimal comissaoGerais, BigDecimal comissaoPlaca,
            BigDecimal comissaoLojistas, BigDecimal totalComissao, BigDecimal salarioTotal
    ) {
        Map<String, Object> auditoria = new LinkedHashMap<>();
        auditoria.put("mes", mesLabel);
        auditoria.put("anoMes", YearMonth.now(ZONE_ID).toString());
        auditoria.put("geradoEm", DATA_HORA_BR.format(agora));
        auditoria.put("salarioFixo", salarioFixo);
        auditoria.put("valorTotalConsertos", valorTotal);
        auditoria.put("comissaoConsertosEmGeral", comissaoGerais);
        auditoria.put("comissaoReparoDePlaca", comissaoPlaca);
        auditoria.put("comissaoConsertoParaLojistas", comissaoLojistas);
        auditoria.put("totalComissao", totalComissao);
        auditoria.put("salarioTotal", salarioTotal);
        auditoria.put("quantidadeConsertos", consertos.size());
        auditoria.put("consertos", consertos.stream().map(this::toMap).toList());
        return auditoria;
    }

    private Map<String, Object> toMap(Conserto c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("dataConserto", c.getDataConserto());
        m.put("numeroOs", c.getNumeroOs());
        m.put("modeloAparelho", c.getModeloAparelho());
        m.put("imei", c.getImei());
        m.put("tipo", c.getTipoConserto().getLabel());
        m.put("descricao", c.getDescricao());
        m.put("valor", c.getValor());
        m.put("comissao", c.getComissao());
        return m;
    }

    private void gerarPdf(File destino, String mesLabel, ZonedDateTime agora, List<Conserto> consertos,
                          BigDecimal valorTotal, BigDecimal totalComissao, BigDecimal salarioTotal) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
        try (FileOutputStream fos = new FileOutputStream(destino)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font cabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

            Paragraph tit = new Paragraph("Relatório de Pagamentos — ControlJob", titulo);
            document.add(tit);
            document.add(new Paragraph("Período: " + mesLabel + " · Gerado em: " + DATA_HORA_BR.format(agora), normal));
            document.add(new Paragraph("Quantidade de consertos: " + consertos.size()
                    + " · Valor total: " + MOEDA.format(valorTotal)
                    + " · Total de comissões: " + MOEDA.format(totalComissao)
                    + " · Salário total: " + MOEDA.format(salarioTotal), normal));
            document.add(new Paragraph(" "));

            PdfPTable tabela = new PdfPTable(8);
            tabela.setWidthPercentage(100);
            tabela.setWidths(new float[]{1.3f, 1.2f, 1.9f, 1.7f, 1.6f, 3.4f, 1.4f, 1.4f});
            for (String h : new String[]{"Data", "OS", "Modelo", "IMEI", "Tipo", "Descrição", "Valor", "Comissão"}) {
                PdfPCell cel = new PdfPCell(new Phrase(h, cabecalho));
                cel.setHorizontalAlignment(Element.ALIGN_LEFT);
                tabela.addCell(cel);
            }
            for (Conserto c : consertos) {
                tabela.addCell(new Phrase(DATA_BR.format(c.getDataConserto()), normal));
                tabela.addCell(new Phrase(nulo(c.getNumeroOs()), normal));
                tabela.addCell(new Phrase(nulo(c.getModeloAparelho()), normal));
                tabela.addCell(new Phrase(c.getImei() != null ? c.getImei() : "—", normal));
                tabela.addCell(new Phrase(c.getTipoConserto().getLabel(), normal));
                tabela.addCell(new Phrase(nulo(c.getDescricao()), normal));
                tabela.addCell(new Phrase(MOEDA.format(c.getValor()), normal));
                tabela.addCell(new Phrase(MOEDA.format(c.getComissao()), normal));
            }
            document.add(tabela);
            document.close();
        }
    }

    private void enviarEmail(String mesLabel, int quantidade, BigDecimal valorTotal, BigDecimal totalComissao,
                             BigDecimal salarioTotal, File arquivoJson, File arquivoPdf) throws MessagingException, java.io.IOException {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        if (emailDestinatarios.isEmpty()) {
            throw new MessagingException(
                    "Nenhum destinatário configurado em controljob.email.destinatario (MAIL_DESTINATARIO).");
        }
        helper.setFrom(emailRemetente);
        helper.setTo(emailDestinatarios.toArray(String[]::new));
        helper.setSubject(emailAssunto + " — " + mesLabel);

        String html = """
                <div style="font-family:Arial, Helvetica, sans-serif; color:#1a1a1a; max-width:640px; margin:0 auto;">
                  <h2 style="margin-bottom:4px;">Relatório Mensal de Pagamentos</h2>
                  <p style="margin-top:0; color:#666;">%s</p>
                  <table style="border-collapse:collapse; width:100%%; font-size:14px;">
                    <tr><td style="padding:6px 0; border-bottom:1px solid #eee;">Consertos registrados</td>
                        <td style="padding:6px 0; border-bottom:1px solid #eee; text-align:right;">%d</td></tr>
                    <tr><td style="padding:6px 0; border-bottom:1px solid #eee;">Valor total dos consertos</td>
                        <td style="padding:6px 0; border-bottom:1px solid #eee; text-align:right;">%s</td></tr>
                    <tr><td style="padding:6px 0; border-bottom:1px solid #eee;">Total de comissões</td>
                        <td style="padding:6px 0; border-bottom:1px solid #eee; text-align:right;">%s</td></tr>
                    <tr><td style="padding:6px 0; border-bottom:1px solid #eee;">Salário total a pagar</td>
                        <td style="padding:6px 0; border-bottom:1px solid #eee; text-align:right; font-weight:bold;">%s</td></tr>
                  </table>
                  <p style="margin-top:16px; font-size:13px; color:#666;">
                    Em anexo seguem o <strong>PDF</strong> com o relatório completo e o arquivo <strong>JSON</strong> de auditoria do mês.
                  </p>
                </div>
                """.formatted(mesLabel, quantidade, MOEDA.format(valorTotal), MOEDA.format(totalComissao), MOEDA.format(salarioTotal));

        helper.setText(html, true);
        helper.addAttachment(arquivoPdf.getName(), arquivoPdf);
        helper.addAttachment(arquivoJson.getName(), arquivoJson);
        mailSender.send(msg);
    }

    private String nulo(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }

    public record Resultado(int quantidadeFechada, String arquivoJson, String arquivoPdf,
                            boolean emailEnviado, String erroEmail) {
    }
}