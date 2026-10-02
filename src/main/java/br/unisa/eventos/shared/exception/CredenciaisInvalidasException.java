package br.unisa.eventos.shared.exception;

import org.springframework.http.HttpStatus;

public class CredenciaisInvalidasException extends AplicacaoException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha invalidos.");
    }

    @Override
    public String codigo() {
        return "CREDENCIAIS_INVALIDAS";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }
}
