package br.unisa.eventos.inscricao.dto;

import br.unisa.eventos.evento.StatusEvento;
import br.unisa.eventos.inscricao.Inscricao;
import br.unisa.eventos.inscricao.StatusInscricao;

import java.time.LocalDateTime;

/**
 * Visao do participante: a inscricao e o evento ao qual ela pertence.
 *
 * <p>{@code presencaRegistrada} existe para a interface saber quando oferecer o certificado:
 * pela RN-05 ele exige a presenca e o fim do evento, e o fim a tela ja tem em {@code evento};
 * a presenca, sem este campo, ela nao tinha como saber.
 */
public record InscricaoResponse(
        Long id,
        StatusInscricao status,
        Integer posicaoFila,
        LocalDateTime dataInscricao,
        boolean presencaRegistrada,
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

    /** Inscricao recem-criada: ainda nao pode ter presenca. */
    public static InscricaoResponse de(Inscricao inscricao) {
        return de(inscricao, false);
    }

    public static InscricaoResponse de(Inscricao inscricao, boolean presencaRegistrada) {
        var evento = inscricao.getEvento();
        return new InscricaoResponse(
                inscricao.getId(), inscricao.getStatus(), inscricao.getPosicaoFila(),
                inscricao.getDataInscricao(), presencaRegistrada,
                new EventoDaInscricao(evento.getId(), evento.getTitulo(), evento.getLocal(),
                        evento.getDataInicio(), evento.getDataFim(), evento.getCargaHoraria(),
                        evento.getStatus()));
    }
}
