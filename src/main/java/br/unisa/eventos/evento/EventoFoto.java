package br.unisa.eventos.evento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "evento_foto")
public class EventoFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private int ordem;

    protected EventoFoto() {
    }

    EventoFoto(Evento evento, String url, int ordem) {
        this.evento = evento;
        this.url = url;
        this.ordem = ordem;
    }

    public Long getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public String getUrl() {
        return url;
    }

    public int getOrdem() {
        return ordem;
    }
}
