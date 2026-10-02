package br.unisa.eventos.shared.exception;

import org.springframework.http.HttpStatus;

public class RegraDeNegocioException extends AplicacaoException {

    private final String codigo;

    public RegraDeNegocioException(String mensagem) {
        this(mensagem, "REGRA_DE_NEGOCIO");
    }

    /** Permite um codigo mais especifico, como EVENTO_LOTADO, mantendo o mesmo status 422. */
    public RegraDeNegocioException(String mensagem, String codigo) {
        super(mensagem);
        this.codigo = codigo;
    }

    @Override
    public String codigo() {
        return codigo;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
