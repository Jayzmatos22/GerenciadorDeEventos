package br.unisa.eventos.ia;

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

/**
 * Trilha de auditoria das chamadas ao modelo: toda chamada e registrada, com ou sem sucesso
 * (secao 8 da especificacao).
 */
@Entity
@Table(name = "interacao_ia")
public class InteracaoIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoInteracaoIA tipo;

    @Column(name = "texto_entrada", nullable = false, columnDefinition = "text")
    private String textoEntrada;

    @Column(name = "json_extraido", columnDefinition = "text")
    private String jsonExtraido;

    @Column(nullable = false)
    private boolean sucesso;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    protected InteracaoIA() {
    }

    InteracaoIA(Usuario usuario, TipoInteracaoIA tipo, String textoEntrada, String jsonExtraido,
                boolean sucesso) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.textoEntrada = textoEntrada;
        this.jsonExtraido = jsonExtraido;
        this.sucesso = sucesso;
    }

    public Long getId() {
        return id;
    }

    public TipoInteracaoIA getTipo() {
        return tipo;
    }

    public String getTextoEntrada() {
        return textoEntrada;
    }

    public String getJsonExtraido() {
        return jsonExtraido;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
