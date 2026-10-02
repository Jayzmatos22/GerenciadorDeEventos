package br.unisa.eventos.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "e obrigatorio") String email,
        @NotBlank(message = "e obrigatoria") String senha
) {
}
