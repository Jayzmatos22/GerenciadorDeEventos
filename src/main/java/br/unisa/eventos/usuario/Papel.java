package br.unisa.eventos.usuario;

public enum Papel {
    PARTICIPANTE,
    ORGANIZADOR;

    /** Nome da authority esperada pelo Spring Security em hasRole(...). */
    public String authority() {
        return "ROLE_" + name();
    }
}
