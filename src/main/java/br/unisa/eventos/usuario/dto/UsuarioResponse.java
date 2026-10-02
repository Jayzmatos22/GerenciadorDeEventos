package br.unisa.eventos.usuario.dto;

import br.unisa.eventos.usuario.Papel;
import br.unisa.eventos.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.Set;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Set<Papel> papeis,
        LocalDateTime criadoEm
) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.papeis(), usuario.getCriadoEm());
    }
}
