package com.controljob.controller;

import com.controljob.service.RelatorioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/relatorio")
    public String relatorio(Model model) {
        var dados = relatorioService.gerarRelatorio();
        model.addAttribute("dados", dados);
        model.addAttribute("pageTitle", "Relatório de Pagamentos");
        return "relatorio";
    }
}
