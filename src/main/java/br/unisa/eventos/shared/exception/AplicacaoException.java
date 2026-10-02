package br.unisa.eventos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Raiz das excecoes de negocio. Cada subclasse carrega o codigo e o status HTTP que o
 * GlobalExceptionHandler devolve, de modo que a lista de codigos da especificacao fique
 * concentrada aqui e nao espalhada pelos controllers.
 */
public abstract class AplicacaoException extends RuntimeException {

    protected AplicacaoException(String mensagem) {
        super(mensagem);
    }

    protected AplicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public abstract String codigo();

    public abstract HttpStatus status();
}
