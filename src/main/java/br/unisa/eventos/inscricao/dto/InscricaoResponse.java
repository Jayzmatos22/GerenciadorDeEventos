package br.unisa.eventos.inscricao.dto;

import br.unisa.eventos.evento.StatusEvento;
import br.unisa.eventos.inscricao.Inscricao;
import br.unisa.eventos.inscricao.StatusInscricao;

import java.time.LocalDateTime;

/** Visao do participante: a inscricao e o evento ao qual ela pertence. */
public record InscricaoResponse(
        Long id,
        StatusInscricao status,
        Integer posicaoFila,
        LocalDateTime dataInscricao,
        EventoDaInscricao evento
) {

    public record EventoDaInscricao(
            Long id,
            String titulo,
            String local,
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            int cargaHoraria,
            StatusEvento status
    ) {
    }

    public static InscricaoResponse de(Inscricao inscricao) {
        var evento = inscricao.getEvento();
        return new InscricaoResponse(
                inscricao.getId(), inscricao.getStatus(), inscricao.getPosicaoFila(),
                inscricao.getDataInscricao(),
                new EventoDaInscricao(evento.getId(), evento.getTitulo(), evento.getLocal(),
                        evento.getDataInicio(), evento.getDataFim(), evento.getCargaHoraria(),
                        evento.getStatus()));
    }
}
