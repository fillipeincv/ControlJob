package com.controljob.controller;

import com.controljob.service.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/usuarios")
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listarUsuarios());
        model.addAttribute("form", new NovoUsuarioForm());
        return "usuarios";
    }

    @PostMapping("/usuarios")
    public String criar(@Valid @ModelAttribute("form") NovoUsuarioForm form, BindingResult result, Model model, RedirectAttributes redirect) {
        if (!form.senha.equals(form.confirmacaoSenha)) result.rejectValue("confirmacaoSenha", "senha.diferente", "As senhas não coincidem.");
        if (result.hasErrors()) {
            model.addAttribute("usuarios", usuarioService.listarUsuarios());
            return "usuarios";
        }
        try {
            usuarioService.criarLeitor(form.nomeUsuario, form.senha);
        } catch (IllegalArgumentException e) {
            result.rejectValue("nomeUsuario", "usuario.duplicado", e.getMessage());
            model.addAttribute("usuarios", usuarioService.listarUsuarios());
            return "usuarios";
        }
        redirect.addFlashAttribute("mensagem", "Usuário de leitura criado com sucesso.");
        return "redirect:/usuarios";
    }

    public static class NovoUsuarioForm {
        @NotBlank(message = "Informe um nome de usuário")
        @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}", message = "Use de 3 a 50 letras, números, ponto, hífen ou sublinhado")
        private String nomeUsuario;
        @NotBlank(message = "Informe uma senha inicial")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        private String senha;
        private String confirmacaoSenha;
        public String getNomeUsuario() { return nomeUsuario; }
        public void setNomeUsuario(String nomeUsuario) { this.nomeUsuario = nomeUsuario; }
        public String getSenha() { return senha; }
        public void setSenha(String senha) { this.senha = senha; }
        public String getConfirmacaoSenha() { return confirmacaoSenha; }
        public void setConfirmacaoSenha(String confirmacaoSenha) { this.confirmacaoSenha = confirmacaoSenha; }
    }
}
