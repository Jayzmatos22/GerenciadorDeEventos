package br.unisa.eventos.shared.exception;

import org.springframework.http.HttpStatus;

public class PresencaNaoRegistradaException extends AplicacaoException {

    public PresencaNaoRegistradaException(String mensagem) {
        super(mensagem);
    }

    @Override
    public String codigo() {
        return "PRESENCA_NAO_REGISTRADA";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
