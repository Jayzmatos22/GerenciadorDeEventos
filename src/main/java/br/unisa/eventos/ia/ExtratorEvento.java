package br.unisa.eventos.ia;

import br.unisa.eventos.ia.dto.EventoExtraidoDTO;

/**
 * Porta de saida da extracao de evento por IA. Isola o Spring AI do resto do codigo e permite
 * um extrator falso nos testes, sem chamada de rede (secao 8 da especificacao).
 */
public interface ExtratorEvento {

    /**
     * @throws br.unisa.eventos.shared.exception.IaIndisponivelException em qualquer falha:
     *         timeout, limite de requisicoes, resposta nao parseavel ou modelo nao configurado
     */
    EventoExtraidoDTO extrair(String texto);
}
