package br.unisa.eventos.presenca.dto;

import br.unisa.eventos.presenca.Presenca;

import java.time.LocalDateTime;

public record PresencaResponse(
        Long id,
        Long inscricaoId,
        LocalDateTime dataHoraCheckin,
        Participante participante
) {

    public record Participante(Long id, String nome, String email) {
    }

    public static PresencaResponse de(Presenca presenca) {
        var usuario = presenca.getInscricao().getUsuario();
        return new PresencaResponse(presenca.getId(), presenca.getInscricao().getId(),
                presenca.getDataHoraCheckin(),
                new Participante(usuario.getId(), usuario.getNome(), usuario.getEmail()));
    }
}
