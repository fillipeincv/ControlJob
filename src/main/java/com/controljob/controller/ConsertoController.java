package com.controljob.controller;

import com.controljob.model.Conserto;
import com.controljob.model.TipoConserto;
import com.controljob.service.ConsertoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

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

    @GetMapping("/lojistas")
    public String lojistas(Model model) {
        model.addAttribute("consertos", service.listarLojistas());
        model.addAttribute("resumo", service.gerarResumo());
        return "lojistas";
    }
}
