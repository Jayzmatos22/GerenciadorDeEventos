package br.unisa.eventos.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "usuario_papel")
public class UsuarioPapel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    protected UsuarioPapel() {
    }

    UsuarioPapel(Usuario usuario, Papel papel) {
        this.usuario = usuario;
        this.papel = papel;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Papel getPapel() {
        return papel;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof UsuarioPapel up
                && papel == up.papel
                && Objects.equals(idDoUsuario(), up.idDoUsuario());
    }

    @Override
    public int hashCode() {
        return Objects.hash(papel, idDoUsuario());
    }

    private Long idDoUsuario() {
        return usuario == null ? null : usuario.getId();
    }
}
