package br.unisa.eventos.presenca;

import br.unisa.eventos.inscricao.Inscricao;
import br.unisa.eventos.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro de presenca no evento. A existencia desta linha e o que torna a inscricao
 * "presente": PRESENTE nao e um valor de StatusInscricao (secao 4.1 da especificacao).
 */
@Entity
@Table(name = "presenca")
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscricao_id", nullable = false, unique = true)
    private Inscricao inscricao;

    @Column(name = "data_hora_checkin", nullable = false)
    private LocalDateTime dataHoraCheckin = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por", nullable = false)
    private Usuario registradoPor;

    protected Presenca() {
    }

    public Presenca(Inscricao inscricao, Usuario registradoPor) {
        this.inscricao = inscricao;
        this.registradoPor = registradoPor;
    }

    public Long getId() {
        return id;
    }

    public Inscricao getInscricao() {
        return inscricao;
    }

    public LocalDateTime getDataHoraCheckin() {
        return dataHoraCheckin;
    }

    public Usuario getRegistradoPor() {
        return registradoPor;
    }
}
