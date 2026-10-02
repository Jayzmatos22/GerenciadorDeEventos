package br.unisa.eventos.usuario.dto;

import java.time.LocalDateTime;

public record LoginResponse(String token, LocalDateTime expiraEm, UsuarioResponse usuario) {
}
