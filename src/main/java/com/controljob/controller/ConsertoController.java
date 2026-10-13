package com.controljob.controller;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;
import com.controljob.service.ConsertoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
public class ConsertoController {

    private final ConsertoService service;

    public ConsertoController(ConsertoService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("resumo", service.gerarResumo());
        model.addAttribute("consertos", service.listarTodos());
        return "dashboard";
    }

    @GetMapping("/consertos/novo")
    public String novoForm(Model model) {
        Conserto conserto = new Conserto();
        conserto.setDataConserto(LocalDate.now());
        model.addAttribute("conserto", conserto);
        model.addAttribute("tipos", TipoConserto.values());
        model.addAttribute("titulo", "Novo conserto");
        return "conserto-form";
    }

    @GetMapping("/consertos/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        Conserto conserto = service.buscarPorId(id);
        List<Conserto> historico = service.historicoPorImei(conserto.getImei());

        model.addAttribute("conserto", conserto);
        model.addAttribute("historico", historico);
        preencherResumo(model, historico);
        return "conserto-detalhe";
    }

    @GetMapping("/historico")
    public String historico(@RequestParam(required = false) String imei, Model model) {
        if (imei != null && !imei.isBlank()) {
            List<Conserto> historico = service.historicoPorImei(imei);
            model.addAttribute("consertos", historico);
            model.addAttribute("imeiBuscado", imei.trim());
            preencherResumo(model, historico);
        }
        return "historico";
    }

    private void preencherResumo(Model model, List<Conserto> historico) {
        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal comissaoTotal = BigDecimal.ZERO;
        LocalDate primeira = null;
        LocalDate ultima = null;

        for (Conserto c : historico) {
            valorTotal = valorTotal.add(c.getValor());
            comissaoTotal = comissaoTotal.add(c.getComissao());
            if (primeira == null || c.getDataConserto().isBefore(primeira)) {
                primeira = c.getDataConserto();
            }
            if (ultima == null || c.getDataConserto().isAfter(ultima)) {
                ultima = c.getDataConserto();
            }
        }

        model.addAttribute("passagens", historico.size());
        model.addAttribute("valorHistorico", valorTotal);
        model.addAttribute("comissaoHistorico", comissaoTotal);
        model.addAttribute("primeiroConserto", primeira);
        model.addAttribute("ultimoConserto", ultima);
    }

    @GetMapping("/consertos/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("conserto", service.buscarPorId(id));
        model.addAttribute("tipos", TipoConserto.values());
        model.addAttribute("titulo", "Editar conserto");
        return "conserto-form";
    }

    @PostMapping("/consertos")
    public String salvar(
            @Valid @ModelAttribute("conserto") Conserto conserto,
            BindingResult result,
            Model model,
            RedirectAttributes redirect
    ) {
        if (result.hasErrors()) {
            model.addAttribute("tipos", TipoConserto.values());
            model.addAttribute("titulo", conserto.getId() == null ? "Novo conserto" : "Editar conserto");
            return "conserto-form";
        }

        service.salvar(conserto);
        redirect.addFlashAttribute("mensagem", "Conserto salvo com sucesso!");
        return "redirect:/";
    }

    @PostMapping("/consertos/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        service.excluir(id);
        redirect.addFlashAttribute("mensagem", "Conserto excluído.");
        return "redirect:/";
    }

    @PostMapping("/consertos/{id}/retirada")
    public String registrarRetirada(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            RedirectAttributes redirect
    ) {
        try {
            service.registrarRetirada(id, data);
            redirect.addFlashAttribute("mensagem", "Retirada pelo cliente registrada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("mensagem", e.getMessage());
        }
        return "redirect:/consertos/" + id;
    }

    @PostMapping("/consertos/{id}/retirada/cancelar")
    public String desfazerRetirada(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.desfazerRetirada(id);
            redirect.addFlashAttribute("mensagem", "Retirada desfeita. O conserto volta a aguardar retirada.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("mensagem", e.getMessage());
        }
        return "redirect:/consertos/" + id;
    }

    @GetMapping("/lojistas")
    public String lojistas(Model model) {
        model.addAttribute("consertos", service.listarLojistas());
        model.addAttribute("resumo", service.gerarResumo());
        return "lojistas";
    }
}
