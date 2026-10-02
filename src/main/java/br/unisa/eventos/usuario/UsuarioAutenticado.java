package br.unisa.eventos.usuario;

import java.util.Set;

/**
 * Principal da requisicao, montado a partir das claims do JWT. Nao ha consulta ao banco por
 * requisicao: o token carrega id, nome, e-mail e papeis.
 */
public record UsuarioAutenticado(Long id, String nome, String email, Set<Papel> papeis) {

    public boolean temPapel(Papel papel) {
        return papeis.contains(papel);
    }
}
