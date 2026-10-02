package br.unisa.eventos.shared.exception;

import org.springframework.http.HttpStatus;

public class InscricaoDuplicadaException extends AplicacaoException {

    public InscricaoDuplicadaException(String mensagem) {
        super(mensagem);
    }

    @Override
    public String codigo() {
        return "INSCRICAO_DUPLICADA";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
