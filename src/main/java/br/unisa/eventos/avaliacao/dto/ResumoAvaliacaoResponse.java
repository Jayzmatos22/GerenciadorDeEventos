package br.unisa.eventos.avaliacao.dto;

import br.unisa.eventos.avaliacao.ResumoAvaliacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ResumoAvaliacaoResponse(
        Long eventoId,
        String textoResumo,
        BigDecimal notaMedia,
        Integer totalAvaliacoes,
        LocalDateTime geradoEm
) {

    public static ResumoAvaliacaoResponse de(ResumoAvaliacao resumo, Long eventoId) {
        return new ResumoAvaliacaoResponse(eventoId, resumo.getTextoResumo(),
                resumo.getNotaMedia(), resumo.getTotalAvaliacoes(), resumo.getGeradoEm());
    }
}
