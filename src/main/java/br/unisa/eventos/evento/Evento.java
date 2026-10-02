package br.unisa.eventos.evento;

import br.unisa.eventos.usuario.Usuario;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "evento")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizador_id", nullable = false)
    private Usuario organizador;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @Column(name = "local", nullable = false, length = 200)
    private String local;

    @Column(name = "data_inicio", nullable = false)
    private LocalDateTime dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDateTime dataFim;

    @Column(name = "carga_horaria", nullable = false)
    private int cargaHoraria;

    @Column(name = "limite_vagas", nullable = false)
    private int limiteVagas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEvento status = StatusEvento.RASCUNHO;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    /** Lock otimista usado na concorrencia pela ultima vaga (RN-02). */
    @Version
    @Column(nullable = false)
    private long versao;

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("ordem asc, id asc")
    private List<EventoFoto> fotos = new ArrayList<>();

    protected Evento() {
    }

    public Evento(Usuario organizador, String titulo, String descricao, String local,
                  LocalDateTime dataInicio, LocalDateTime dataFim, int cargaHoraria,
                  int limiteVagas) {
        this.organizador = organizador;
        atualizarDados(titulo, descricao, local, dataInicio, dataFim, cargaHoraria, limiteVagas);
    }

    public void atualizarDados(String titulo, String descricao, String local,
                               LocalDateTime dataInicio, LocalDateTime dataFim,
                               int cargaHoraria, int limiteVagas) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.local = local;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.cargaHoraria = cargaHoraria;
        this.limiteVagas = limiteVagas;
    }

    public void mudarStatus(StatusEvento novo) {
        this.status = novo;
    }

    public EventoFoto adicionarFoto(String url) {
        int proximaOrdem = fotos.stream()
                .map(EventoFoto::getOrdem)
                .max(Comparator.naturalOrder())
                .map(ultima -> ultima + 1)
                .orElse(0);

        EventoFoto foto = new EventoFoto(this, url, proximaOrdem);
        fotos.add(foto);
        return foto;
    }

    public boolean removerFoto(Long fotoId) {
        return fotos.removeIf(foto -> foto.getId().equals(fotoId));
    }

    /** RN-08: so o organizador criador age sobre o evento. */
    public boolean pertenceA(Long usuarioId) {
        return organizador.getId().equals(usuarioId);
    }

    public Long getId() {
        return id;
    }

    public Usuario getOrganizador() {
        return organizador;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getLocal() {
        return local;
    }

    public LocalDateTime getDataInicio() {
        return dataInicio;
    }

    public LocalDateTime getDataFim() {
        return dataFim;
    }

    public int getCargaHoraria() {
        return cargaHoraria;
    }

    public int getLimiteVagas() {
        return limiteVagas;
    }

    public StatusEvento getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public long getVersao() {
        return versao;
    }

    public List<EventoFoto> getFotos() {
        return fotos;
    }
}
