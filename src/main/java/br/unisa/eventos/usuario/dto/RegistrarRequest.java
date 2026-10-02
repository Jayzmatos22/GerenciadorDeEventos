package br.unisa.eventos.usuario.dto;

import br.unisa.eventos.usuario.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RN-11: senha de no minimo 8 caracteres.
 * O papel e opcional e, quando ausente, o usuario nasce PARTICIPANTE.
 */
public record RegistrarRequest(
        @NotBlank(message = "e obrigatorio")
        @Size(max = 120, message = "deve ter no maximo 120 caracteres")
        String nome,

        @NotBlank(message = "e obrigatorio")
        @Email(message = "deve ser um e-mail valido")
        @Size(max = 160, message = "deve ter no maximo 160 caracteres")
        String email,

        @NotBlank(message = "e obrigatoria")
        @Size(min = 8, max = 72, message = "deve ter no minimo 8 caracteres")
        String senha,

        Papel papel
) {
}
