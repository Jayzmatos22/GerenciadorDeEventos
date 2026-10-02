package br.unisa.eventos.avaliacao.dto;

import br.unisa.eventos.avaliacao.Avaliacao;

import java.time.LocalDateTime;

public record AvaliacaoResponse(
        Long id,
        Long inscricaoId,
        int nota,
        String comentario,
        LocalDateTime dataEnvio,
        String nomeParticipante
) {

    public static AvaliacaoResponse de(Avaliacao avaliacao) {
        return new AvaliacaoResponse(avaliacao.getId(), avaliacao.getInscricao().getId(),
                avaliacao.getNota(), avaliacao.getComentario(), avaliacao.getDataEnvio(),
                avaliacao.getInscricao().getUsuario().getNome());
    }
}
