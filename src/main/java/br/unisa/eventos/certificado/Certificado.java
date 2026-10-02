package br.unisa.eventos.certificado;

import br.unisa.eventos.inscricao.Inscricao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "certificado")
public class Certificado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscricao_id", nullable = false, unique = true)
    private Inscricao inscricao;

    @Column(name = "codigo_autenticidade", nullable = false, length = 40, unique = true)
    private String codigoAutenticidade;

    @Column(name = "data_emissao", nullable = false)
    private LocalDateTime dataEmissao = LocalDateTime.now();

    /**
     * // DECISAO: na v1 o PDF e gerado sob demanda e nao fica em disco, entao este campo
     * permanece nulo (secao 9 da especificacao). A coluna existe para quando quiserem guardar.
     */
    @Column(name = "arquivo_url", length = 500)
    private String arquivoUrl;

    protected Certificado() {
    }

    public Certificado(Inscricao inscricao) {
        this.inscricao = inscricao;
        this.codigoAutenticidade = gerarCodigo();
    }

    /** RN-05: codigo de autenticidade e um UUID sem hifens, em maiusculas. */
    private static String gerarCodigo() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public Inscricao getInscricao() {
        return inscricao;
    }

    public String getCodigoAutenticidade() {
        return codigoAutenticidade;
    }

    public LocalDateTime getDataEmissao() {
        return dataEmissao;
    }

    public String getArquivoUrl() {
        return arquivoUrl;
    }
}
