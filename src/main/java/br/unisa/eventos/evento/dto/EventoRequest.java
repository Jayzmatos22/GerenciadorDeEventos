package br.unisa.eventos.evento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Corpo de criacao e edicao de evento.
 *
 * <p>// DECISAO: nao ha {@code @Future} em dataInicio. A avaliacao pos-evento (RN-06) exige
 * que dataFim ja tenha passado, e os testes precisam montar esse cenario. A coerencia entre as
 * duas datas e checada no service, espelhando a constraint ck_evento_periodo do banco.
 */
public record EventoRequest(
        @NotBlank(message = "e obrigatorio")
        @Size(max = 160, message = "deve ter no maximo 160 caracteres")
        String titulo,

        @NotBlank(message = "e obrigatoria")
        String descricao,

        @NotBlank(message = "e obrigatorio")
        @Size(max = 200, message = "deve ter no maximo 200 caracteres")
        String local,

        @NotNull(message = "e obrigatoria")
        LocalDateTime dataInicio,

        @NotNull(message = "e obrigatoria")
        LocalDateTime dataFim,

        @NotNull(message = "e obrigatoria")
        @Positive(message = "deve ser maior que zero")
        Integer cargaHoraria,

        @NotNull(message = "e obrigatorio")
        @Positive(message = "deve ser maior que zero")
        Integer limiteVagas
) {
}
