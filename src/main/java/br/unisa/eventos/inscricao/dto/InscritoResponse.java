package br.unisa.eventos.inscricao.dto;

import br.unisa.eventos.inscricao.Inscricao;
import br.unisa.eventos.inscricao.StatusInscricao;

import java.time.LocalDateTime;

/** Visao do organizador: quem esta inscrito e em que posicao da fila. */
public record InscritoResponse(
        Long id,
        StatusInscricao status,
        Integer posicaoFila,
        LocalDateTime dataInscricao,
        Participante participante
) {

    public record Participante(Long id, String nome, String email) {
    }

    public static InscritoResponse de(Inscricao inscricao) {
        var usuario = inscricao.getUsuario();
        return new InscritoResponse(
                inscricao.getId(), inscricao.getStatus(), inscricao.getPosicaoFila(),
                inscricao.getDataInscricao(),
                new Participante(usuario.getId(), usuario.getNome(), usuario.getEmail()));
    }
}
