package com.controljob.controller;

import com.controljob.service.FechamentoMesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FechamentoMesController {

    private static final Logger log = LoggerFactory.getLogger(FechamentoMesController.class);

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
            log.error("Falha ao fechar o mês manualmente", e);
            redirect.addFlashAttribute("mensagem",
                    "Não foi possível concluir o fechamento. Nenhum registro foi arquivado; avise o administrador.");
        }
        return "redirect:/";
    }
}