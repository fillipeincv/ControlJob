package com.controljob.config;

import com.controljob.exception.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView naoEncontrado(RecursoNaoEncontradoException excecao) {
        log.info("Recurso não encontrado: {}", excecao.getMessage());
        return erro(HttpStatus.NOT_FOUND, "O registro solicitado não foi encontrado.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView recursoEstaticoAusente(NoResourceFoundException excecao) {
        return erro(HttpStatus.NOT_FOUND, "Página não encontrada.");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView requisicaoInvalida(Exception excecao) {
        log.warn("Requisição inválida: {}", excecao.getMessage());
        return erro(HttpStatus.BAD_REQUEST, "Não foi possível processar a solicitação. Confira os dados e tente novamente.");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ModelAndView estadoInvalido(IllegalStateException excecao) {
        log.warn("Operação não permitida: {}", excecao.getMessage());
        return erro(HttpStatus.CONFLICT, excecao.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView erroInesperado(Exception excecao) {
        log.error("Erro inesperado ao processar a requisição", excecao);
        return erro(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente ou avise o administrador.");
    }

    private ModelAndView erro(HttpStatus status, String mensagem) {
        ModelAndView mav = new ModelAndView("erro");
        mav.addObject("status", status.value());
        mav.addObject("mensagem", mensagem);
        mav.setStatus(status);
        return mav;
    }
}
