package com.controljob.controller;

import com.controljob.model.Agrupamento;
import com.controljob.model.Auditoria;
import com.controljob.model.SituacaoConserto;
import com.controljob.model.TipoConserto;
import com.controljob.service.AuditoriaService;
import com.controljob.service.EstatisticaService;
import com.controljob.service.SalarioConfigService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Controller
public class AdminController {

    private final EstatisticaService estatisticaService;
    private final AuditoriaService auditoriaService;
    private final SalarioConfigService salarioConfigService;

    public AdminController(
            EstatisticaService estatisticaService,
            AuditoriaService auditoriaService,
            SalarioConfigService salarioConfigService
    ) {
        this.estatisticaService = estatisticaService;
        this.auditoriaService = auditoriaService;
        this.salarioConfigService = salarioConfigService;
    }

    @GetMapping("/admin")
    public String painel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            Model model
    ) {
        if (de == null && ate == null) {
            YearMonth mes = YearMonth.now();
            de = mes.atDay(1);
            ate = mes.atEndOfMonth();
        }

        model.addAttribute("painel", estatisticaService.gerarPainel(de, ate));
        model.addAttribute("salarioFixo", salarioConfigService.salarioAtual());
        model.addAttribute("de", de);
        model.addAttribute("ate", ate);
        model.addAttribute("pagina", "painel");
        return "admin/painel";
    }

    @PostMapping("/admin/salario")
    public String atualizarSalario(@RequestParam BigDecimal valor, RedirectAttributes redirect) {
        try {
            BigDecimal novo = salarioConfigService.atualizar(valor);
            redirect.addFlashAttribute("mensagem",
                    "Salário fixo atualizado para R$ " + novo.toPlainString() + ".");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensagem", "Não foi possível atualizar: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @GetMapping("/admin/auditoria")
    public String auditoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) List<TipoConserto> tipo,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal valorMin,
            @RequestParam(required = false) BigDecimal valorMax,
            @RequestParam(required = false) SituacaoConserto situacao,
            @RequestParam(required = false) Agrupamento agrupar,
            Model model
    ) {
        if (de == null && ate == null) {
            YearMonth mes = YearMonth.now();
            de = mes.atDay(1);
            ate = mes.atEndOfMonth();
        }

        if (situacao == null) {
            situacao = SituacaoConserto.TODOS;
        }
        if (agrupar == null) {
            agrupar = Agrupamento.NENHUM;
        }

        Auditoria.Criterios criterios = new Auditoria.Criterios(
                de, ate, tipo == null ? List.of() : tipo, modelo, q, valorMin, valorMax, situacao);

        model.addAttribute("auditoria", auditoriaService.auditar(criterios, agrupar));
        model.addAttribute("tipos", TipoConserto.values());
        model.addAttribute("situacoes", SituacaoConserto.values());
        model.addAttribute("agrupamentos", Agrupamento.values());
        model.addAttribute("de", de);
        model.addAttribute("ate", ate);
        model.addAttribute("tipo", tipo == null ? List.of() : tipo);
        model.addAttribute("modelo", modelo);
        model.addAttribute("q", q);
        model.addAttribute("valorMin", valorMin);
        model.addAttribute("valorMax", valorMax);
        model.addAttribute("situacao", situacao);
        model.addAttribute("agrupar", agrupar);
        model.addAttribute("pagina", "auditoria");
        return "admin/auditoria";
    }

    @GetMapping("/admin/auditoria/csv")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) List<TipoConserto> tipo,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal valorMin,
            @RequestParam(required = false) BigDecimal valorMax,
            @RequestParam(required = false) SituacaoConserto situacao
    ) {
        if (de == null && ate == null) {
            YearMonth mes = YearMonth.now();
            de = mes.atDay(1);
            ate = mes.atEndOfMonth();
        }

        Auditoria.Criterios criterios = new Auditoria.Criterios(
                de, ate, tipo == null ? List.of() : tipo, modelo, q, valorMin, valorMax,
                situacao == null ? SituacaoConserto.TODOS : situacao);

        Auditoria resultado = auditoriaService.auditar(criterios, Agrupamento.NENHUM);
        String csv = auditoriaService.gerarCsv(resultado);
        String nomeArquivo = "auditoria-" + (de != null ? de : "inicio") + "-a-" + (ate != null ? ate : "hoje") + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
