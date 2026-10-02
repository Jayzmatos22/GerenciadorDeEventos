package br.unisa.eventos.ia;

import java.util.List;

/**
 * Porta de saida do resumo de avaliacoes (RF-10). Mesma politica de erro do extrator: falha
 * virou IA_INDISPONIVEL, e existe implementacao falsa nos testes.
 */
public interface ResumidorAvaliacoes {

    /**
     * @param comentarios comentarios livres dos participantes
     * @return texto corrido de 3 a 5 frases, em portugues, sem citar participantes
     * @throws br.unisa.eventos.shared.exception.IaIndisponivelException em qualquer falha
     */
    String resumir(List<String> comentarios);
}
