package com.controljob.controller;

import com.controljob.service.UsuarioService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;

@Controller
public class AuthController {
    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/configurar-admin")
    public String configurarAdminForm(Model model) {
        if (!usuarioService.precisaConfigurarAdministrador()) return "redirect:/login";
        model.addAttribute("form", new ConfigurarAdminForm());
        model.addAttribute("nomeAdministrador", usuarioService.getNomeAdministrador());
        return "configurar-admin";
    }

    @PostMapping("/configurar-admin")
    public String configurarAdmin(@Valid @ModelAttribute("form") ConfigurarAdminForm form, BindingResult result, Model model) {
        if (!usuarioService.precisaConfigurarAdministrador()) return "redirect:/login";
        if (!form.senha.equals(form.confirmacaoSenha)) result.rejectValue("confirmacaoSenha", "senha.diferente", "As senhas não coincidem.");
        if (result.hasErrors()) {
            model.addAttribute("nomeAdministrador", usuarioService.getNomeAdministrador());
            return "configurar-admin";
        }
        usuarioService.configurarAdministrador(form.senha);
        return "redirect:/login?configurado";
    }

    @GetMapping("/login")
    public String login() {
        return usuarioService.precisaConfigurarAdministrador() ? "redirect:/configurar-admin" : "login";
    }

    public static class ConfigurarAdminForm {
        @NotBlank(message = "Informe uma senha")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        private String senha;
        private String confirmacaoSenha;
        public String getSenha() { return senha; }
        public void setSenha(String senha) { this.senha = senha; }
        public String getConfirmacaoSenha() { return confirmacaoSenha; }
        public void setConfirmacaoSenha(String confirmacaoSenha) { this.confirmacaoSenha = confirmacaoSenha; }
    }
}
