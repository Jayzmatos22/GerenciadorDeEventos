package br.unisa.eventos.evento.dto;

import br.unisa.eventos.evento.EventoFoto;

public record EventoFotoResponse(Long id, String url, int ordem) {

    public static EventoFotoResponse de(EventoFoto foto) {
        return new EventoFotoResponse(foto.getId(), foto.getUrl(), foto.getOrdem());
    }
}
