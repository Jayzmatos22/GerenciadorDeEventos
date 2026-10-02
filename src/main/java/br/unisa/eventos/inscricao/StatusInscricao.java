package br.unisa.eventos.inscricao;

public enum StatusInscricao {

    CONFIRMADA,
    EM_ESPERA,
    CANCELADA,

    /**
     * // DECISAO: nenhum fluxo da v1 grava AUSENTE. O valor existe porque e parte do contrato
     * de enums da especificacao, mas marcar ausencia em lote ao encerrar o evento nao e exigido
     * por nenhum requisito funcional. Ausencia hoje e a falta de registro em presenca.
     */
    AUSENTE;

    /** Inscricao ativa e a que ocupa vaga ou lugar na fila. */
    public boolean ativa() {
        return this == CONFIRMADA || this == EM_ESPERA;
    }
}
