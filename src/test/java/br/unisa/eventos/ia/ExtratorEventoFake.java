package br.unisa.eventos.ia;

import br.unisa.eventos.ia.dto.EventoExtraidoDTO;
import br.unisa.eventos.shared.exception.IaIndisponivelException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Extrator de teste, sem chamada de rede (secao 8 da especificacao). Permite escolher entre
 * uma resposta pronta e uma falha, para exercitar os dois lados da RN-10.
 */
public class ExtratorEventoFake implements ExtratorEvento {

    private EventoExtraidoDTO resposta = rascunhoCompleto();
    private boolean deveFalhar;
    private String ultimoTextoRecebido;

    public static EventoExtraidoDTO rascunhoCompleto() {
        return new EventoExtraidoDTO(
                "Semana de Tecnologia", "Palestras e oficinas de tecnologia.",
                "Auditorio UNISA", LocalDateTime.of(2026, 11, 10, 19, 0),
                LocalDateTime.of(2026, 11, 10, 22, 0), 3, 120, List.of());
    }

    public void responderCom(EventoExtraidoDTO novaResposta) {
        this.resposta = novaResposta;
        this.deveFalhar = false;
    }

    public void falharNaProximaChamada() {
        this.deveFalhar = true;
    }

    public void voltarAFuncionar() {
        this.deveFalhar = false;
        this.resposta = rascunhoCompleto();
    }

    public String ultimoTextoRecebido() {
        return ultimoTextoRecebido;
    }

    @Override
    public EventoExtraidoDTO extrair(String texto) {
        this.ultimoTextoRecebido = texto;

        if (deveFalhar) {
            throw new IaIndisponivelException("Falha simulada do modelo de IA.");
        }
        return resposta;
    }
}
