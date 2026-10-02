package br.unisa.eventos.shared.exception;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoException extends AplicacaoException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public static RecursoNaoEncontradoException de(String recurso, Object id) {
        return new RecursoNaoEncontradoException("%s %s nao encontrado.".formatted(recurso, id));
    }

    @Override
    public String codigo() {
        return "RECURSO_NAO_ENCONTRADO";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
