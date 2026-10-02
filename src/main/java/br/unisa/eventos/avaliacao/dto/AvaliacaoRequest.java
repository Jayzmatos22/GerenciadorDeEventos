package br.unisa.eventos.avaliacao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AvaliacaoRequest(
        @NotNull(message = "e obrigatoria")
        @Min(value = 1, message = "deve ser entre 1 e 5")
        @Max(value = 5, message = "deve ser entre 1 e 5")
        Integer nota,

        @Size(max = 2000, message = "deve ter no maximo 2000 caracteres")
        String comentario
) {
}
