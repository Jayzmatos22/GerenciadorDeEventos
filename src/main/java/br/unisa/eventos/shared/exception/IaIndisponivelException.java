package br.unisa.eventos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * RN-10: falha na IA nunca bloqueia o fluxo. O cliente recebe 503 com codigo
 * IA_INDISPONIVEL e abre o formulario manual.
 */
public class IaIndisponivelException extends AplicacaoException {

    public IaIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public IaIndisponivelException(String mensagem) {
        super(mensagem);
    }

    @Override
    public String codigo() {
        return "IA_INDISPONIVEL";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
