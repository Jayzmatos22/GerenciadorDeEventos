package br.unisa.eventos.avaliacao;

import br.unisa.eventos.evento.Evento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resumo das avaliacoes de um evento, gerado por IA (RN-07). Ha no maximo um por evento:
 * regerar substitui o anterior em vez de acumular versoes.
 */
@Entity
@Table(name = "resumo_avaliacao")
public class ResumoAvaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false, unique = true)
    private Evento evento;

    @Column(name = "texto_resumo", nullable = false, columnDefinition = "text")
    private String textoResumo;

    @Column(name = "nota_media", precision = 3, scale = 2)
    private BigDecimal notaMedia;

    @Column(name = "total_avaliacoes")
    private Integer totalAvaliacoes;

    @Column(name = "gerado_em", nullable = false)
    private LocalDateTime geradoEm = LocalDateTime.now();

    protected ResumoAvaliacao() {
    }

    public ResumoAvaliacao(Evento evento, String textoResumo, BigDecimal notaMedia,
                           int totalAvaliacoes) {
        this.evento = evento;
        atualizar(textoResumo, notaMedia, totalAvaliacoes);
    }

    public void atualizar(String textoResumo, BigDecimal notaMedia, int totalAvaliacoes) {
        this.textoResumo = textoResumo;
        this.notaMedia = notaMedia;
        this.totalAvaliacoes = totalAvaliacoes;
        this.geradoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public String getTextoResumo() {
        return textoResumo;
    }

    public BigDecimal getNotaMedia() {
        return notaMedia;
    }

    public Integer getTotalAvaliacoes() {
        return totalAvaliacoes;
    }

    public LocalDateTime getGeradoEm() {
        return geradoEm;
    }
}
