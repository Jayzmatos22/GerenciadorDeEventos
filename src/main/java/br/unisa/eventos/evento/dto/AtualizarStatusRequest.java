package br.unisa.eventos.evento.dto;

import br.unisa.eventos.evento.StatusEvento;
import jakarta.validation.constraints.NotNull;

public record AtualizarStatusRequest(@NotNull(message = "e obrigatorio") StatusEvento status) {
}
