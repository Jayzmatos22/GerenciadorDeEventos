package br.unisa.eventos.evento.dto;

import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.evento.StatusEvento;

import java.time.LocalDateTime;
import java.util.List;

public record EventoResponse(
        Long id,
        String titulo,
        String descricao,
        String local,
        LocalDateTime dataInicio,
        LocalDateTime dataFim,
        int cargaHoraria,
        int limiteVagas,
        long vagasRestantes,
        StatusEvento status,
        LocalDateTime criadoEm,
        Organizador organizador,
        List<EventoFotoResponse> fotos
) {

    public record Organizador(Long id, String nome) {
    }

    public static EventoResponse de(Evento evento, long vagasOcupadas) {
        return new EventoResponse(
                evento.getId(), evento.getTitulo(), evento.getDescricao(), evento.getLocal(),
                evento.getDataInicio(), evento.getDataFim(), evento.getCargaHoraria(),
                evento.getLimiteVagas(), Math.max(0, evento.getLimiteVagas() - vagasOcupadas),
                evento.getStatus(), evento.getCriadoEm(),
                new Organizador(evento.getOrganizador().getId(), evento.getOrganizador().getNome()),
                evento.getFotos().stream().map(EventoFotoResponse::de).toList());
    }
}
