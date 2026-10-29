package com.controljob.controller;

import com.controljob.dto.ConsertoForm;
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
import java.time.ZoneId;
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
        ConsertoForm conserto = new ConsertoForm();
        conserto.setDataConserto(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
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
    public String editarForm(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        Conserto conserto = service.buscarPorId(id);
        if (conserto.getDataFechamento() != null) {
            redirect.addFlashAttribute("mensagem", "Conserto já fechado no mês e não pode ser alterado.");
            return "redirect:/consertos/" + id;
        }
        model.addAttribute("conserto", ConsertoForm.from(conserto));
        model.addAttribute("tipos", TipoConserto.values());
        model.addAttribute("titulo", "Editar conserto");
        return "conserto-form";
    }

    @PostMapping("/consertos")
    public String salvar(
            @Valid @ModelAttribute("conserto") ConsertoForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect
    ) {
        if (result.hasErrors()) {
            model.addAttribute("tipos", TipoConserto.values());
            model.addAttribute("titulo", form.getId() == null ? "Novo conserto" : "Editar conserto");
            return "conserto-form";
        }

        try {
            Conserto conserto = form.getId() == null
                    ? form.paraNovo()
                    : preencherExistente(form);
            service.salvar(conserto);
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("mensagem", e.getMessage());
            return "redirect:/consertos/" + form.getId();
        }
        redirect.addFlashAttribute("mensagem", "Conserto salvo com sucesso!");
        return "redirect:/";
    }

    private Conserto preencherExistente(ConsertoForm form) {
        Conserto conserto = service.buscarPorId(form.getId());
        form.aplicarEm(conserto);
        return conserto;
    }

    @PostMapping("/consertos/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.excluir(id);
            redirect.addFlashAttribute("mensagem", "Conserto excluído.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("mensagem", e.getMessage());
        }
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
