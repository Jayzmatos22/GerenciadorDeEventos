package br.unisa.eventos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Lancada pelos services quando o usuario autenticado nao e dono do recurso (RN-08).
 * Propriedade do recurso nao se resolve por anotacao, por isso a verificacao vive no service.
 */
public class AcessoNegadoException extends AplicacaoException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }

    @Override
    public String codigo() {
        return "ACESSO_NEGADO";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
