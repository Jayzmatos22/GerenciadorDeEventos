package br.unisa.eventos.inscricao;

import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.usuario.Usuario;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "inscricao")
public class Inscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusInscricao status;

    /** Preenchido apenas em EM_ESPERA; comeca em 1 e e renumerado a cada saida da fila. */
    @Column(name = "posicao_fila")
    private Integer posicaoFila;

    @Column(name = "data_inscricao", nullable = false)
    private LocalDateTime dataInscricao = LocalDateTime.now();

    protected Inscricao() {
    }

    private Inscricao(Usuario usuario, Evento evento, StatusInscricao status, Integer posicaoFila) {
        this.usuario = usuario;
        this.evento = evento;
        this.status = status;
        this.posicaoFila = posicaoFila;
    }

    public static Inscricao confirmada(Usuario usuario, Evento evento) {
        return new Inscricao(usuario, evento, StatusInscricao.CONFIRMADA, null);
    }

    public static Inscricao emEspera(Usuario usuario, Evento evento, int posicaoFila) {
        return new Inscricao(usuario, evento, StatusInscricao.EM_ESPERA, posicaoFila);
    }

    /** RN-02: promocao da primeira da fila quando uma confirmada e cancelada. */
    public void promover() {
        this.status = StatusInscricao.CONFIRMADA;
        this.posicaoFila = null;
    }

    public void cancelar() {
        this.status = StatusInscricao.CANCELADA;
        this.posicaoFila = null;
    }

    public void reposicionarFila(int novaPosicao) {
        this.posicaoFila = novaPosicao;
    }

    public boolean pertenceA(Long usuarioId) {
        return usuario.getId().equals(usuarioId);
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Evento getEvento() {
        return evento;
    }

    public StatusInscricao getStatus() {
        return status;
    }

    public Integer getPosicaoFila() {
        return posicaoFila;
    }

    public LocalDateTime getDataInscricao() {
        return dataInscricao;
    }
}
