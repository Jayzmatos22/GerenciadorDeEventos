package br.unisa.eventos.ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterpretarEventoRequest(
        @NotBlank(message = "e obrigatorio")
        @Size(max = 5000, message = "deve ter no maximo 5000 caracteres")
        String texto
) {
}
