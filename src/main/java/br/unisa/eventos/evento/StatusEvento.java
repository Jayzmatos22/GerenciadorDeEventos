package br.unisa.eventos.evento;

import java.util.Set;

public enum StatusEvento {

    RASCUNHO,
    PUBLICADO,
    EM_ANDAMENTO,
    ENCERRADO,
    CANCELADO;

    // DECISAO: a especificacao define quais estados sao terminais (RN-09) mas nao a maquina de
    // transicoes completa. Adotado o caminho mais simples que atende a regra: o ciclo de vida
    // segue em frente e o cancelamento e possivel enquanto o evento nao terminou.
    private static final Set<StatusEvento> TERMINAIS = Set.of(ENCERRADO, CANCELADO);

    public boolean podeTransitarPara(StatusEvento destino) {
        return switch (this) {
            case RASCUNHO -> destino == PUBLICADO || destino == CANCELADO;
            case PUBLICADO -> destino == EM_ANDAMENTO || destino == ENCERRADO || destino == CANCELADO;
            case EM_ANDAMENTO -> destino == ENCERRADO || destino == CANCELADO;
            case ENCERRADO, CANCELADO -> false;
        };
    }

    /** RN-09: evento ENCERRADO ou CANCELADO nao aceita edicao nem novas inscricoes. */
    public boolean terminal() {
        return TERMINAIS.contains(this);
    }

    /** RN-09: evento so aceita inscricao em PUBLICADO. */
    public boolean aceitaInscricao() {
        return this == PUBLICADO;
    }
}
