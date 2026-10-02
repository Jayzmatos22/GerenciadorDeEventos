package br.unisa.eventos.presenca.dto;

import jakarta.validation.constraints.NotNull;

public record RegistrarPresencaRequest(@NotNull(message = "e obrigatorio") Long inscricaoId) {
}
