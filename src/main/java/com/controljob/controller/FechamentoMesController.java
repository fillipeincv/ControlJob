package com.controljob.controller;

import com.controljob.service.FechamentoMesService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FechamentoMesController {

    private final FechamentoMesService service;

    public FechamentoMesController(FechamentoMesService service) {
        this.service = service;
    }

    @PostMapping("/admin/fechar-mes")
    public String fecharMes(RedirectAttributes redirect) {
        try {
            FechamentoMesService.Resultado resultado = service.fecharMes();
            String mensagem = String.format(
                    "Mês fechado: %d conserto(s) arquivado(s). E-mail enviado: %s",
                    resultado.quantidadeFechada(),
                    resultado.emailEnviado() ? "sim" : "não — " + resultado.erroEmail());
            redirect.addFlashAttribute("mensagem", mensagem);
        } catch (Exception e) {
            redirect.addFlashAttribute("mensagem", "Falha ao fechar o mês: " + e.getMessage());
        }
        return "redirect:/";
    }
}