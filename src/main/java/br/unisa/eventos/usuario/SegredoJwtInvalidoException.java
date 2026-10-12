package br.unisa.eventos.usuario;

/**
 * Levantada na subida quando {@code app.jwt.segredo} esta ausente ou curto demais.
 *
 * <p>Tem tipo proprio por causa do {@link br.unisa.eventos.config.SegredoJwtFailureAnalyzer}:
 * o Spring Boot so consegue explicar uma falha de inicializacao de forma legivel se puder
 * reconhecer a excecao pelo tipo. Sem isso, a causa real fica enterrada sob tres niveis de
 * BeanCreationException e quem esta subindo o projeto pela primeira vez nao acha.
 */
public class SegredoJwtInvalidoException extends RuntimeException {

    public SegredoJwtInvalidoException(String mensagem) {
        super(mensagem);
    }
}
