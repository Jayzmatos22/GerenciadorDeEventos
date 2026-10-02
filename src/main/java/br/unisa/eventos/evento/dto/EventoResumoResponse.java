package br.unisa.eventos.evento.dto;

import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.evento.StatusEvento;

import java.time.LocalDateTime;

/** Item da listagem: o suficiente para montar um card, sem descricao completa nem fotos. */
public record EventoResumoResponse(
        Long id,
        String titulo,
        String local,
        LocalDateTime dataInicio,
        LocalDateTime dataFim,
        int cargaHoraria,
        int limiteVagas,
        long vagasRestantes,
        StatusEvento status
) {

    public static EventoResumoResponse de(Evento evento, long vagasOcupadas) {
        return new EventoResumoResponse(
                evento.getId(), evento.getTitulo(), evento.getLocal(),
                evento.getDataInicio(), evento.getDataFim(), evento.getCargaHoraria(),
                evento.getLimiteVagas(), Math.max(0, evento.getLimiteVagas() - vagasOcupadas),
                evento.getStatus());
    }
}
