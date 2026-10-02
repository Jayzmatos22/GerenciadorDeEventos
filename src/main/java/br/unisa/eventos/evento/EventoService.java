package br.unisa.eventos.evento;

import br.unisa.eventos.evento.dto.AtualizarStatusRequest;
import br.unisa.eventos.evento.dto.EventoFotoResponse;
import br.unisa.eventos.evento.dto.EventoRequest;
import br.unisa.eventos.evento.dto.EventoResponse;
import br.unisa.eventos.evento.dto.EventoResumoResponse;
import br.unisa.eventos.shared.exception.AcessoNegadoException;
import br.unisa.eventos.shared.exception.RecursoNaoEncontradoException;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import br.unisa.eventos.storage.ArmazenamentoArquivo;
import br.unisa.eventos.usuario.Usuario;
import br.unisa.eventos.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class EventoService {

    private static final Logger log = LoggerFactory.getLogger(EventoService.class);

    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ArmazenamentoArquivo armazenamento;

    public EventoService(EventoRepository eventoRepository, UsuarioRepository usuarioRepository,
                         ArmazenamentoArquivo armazenamento) {
        this.eventoRepository = eventoRepository;
        this.usuarioRepository = usuarioRepository;
        this.armazenamento = armazenamento;
    }

    /** RF-04: catalogo publico, so eventos PUBLICADOS, com filtros opcionais. */
    @Transactional(readOnly = true)
    public Page<EventoResumoResponse> listarPublicados(String termo, LocalDateTime de,
                                                       LocalDateTime ate, Pageable paginacao) {
        Specification<Evento> filtro = EventoSpecifications
                .comStatus(StatusEvento.PUBLICADO)
                .and(EventoSpecifications.contendoTermo(termo))
                .and(EventoSpecifications.comecandoApos(de))
                .and(EventoSpecifications.comecandoAntes(ate));

        return eventoRepository.findAll(filtro, paginacao)
                .map(evento -> EventoResumoResponse.de(
                        evento, eventoRepository.contarConfirmadas(evento.getId())));
    }

    @Transactional(readOnly = true)
    public Page<EventoResumoResponse> listarDoOrganizador(Long organizadorId, Pageable paginacao) {
        return eventoRepository.findByOrganizadorIdOrderByDataInicioDesc(organizadorId, paginacao)
                .map(evento -> EventoResumoResponse.de(
                        evento, eventoRepository.contarConfirmadas(evento.getId())));
    }

    @Transactional(readOnly = true)
    public EventoResponse buscar(Long id) {
        Evento evento = eventoRepository.buscarComFotos(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Evento", id));
        return EventoResponse.de(evento, eventoRepository.contarConfirmadas(id));
    }

    /** RF-02: evento nasce em RASCUNHO. */
    @Transactional
    public EventoResponse criar(Long organizadorId, EventoRequest requisicao) {
        validarPeriodo(requisicao.dataInicio(), requisicao.dataFim());

        Usuario organizador = usuarioRepository.findById(organizadorId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", organizadorId));

        Evento evento = new Evento(organizador, requisicao.titulo().trim(),
                requisicao.descricao().trim(), requisicao.local().trim(), requisicao.dataInicio(),
                requisicao.dataFim(), requisicao.cargaHoraria(), requisicao.limiteVagas());

        Evento salvo = eventoRepository.save(evento);
        log.info("Evento {} criado pelo organizador {}", salvo.getId(), organizadorId);
        return EventoResponse.de(salvo, 0);
    }

    /** RN-08 e RN-09: so o dono edita, e nao se edita evento terminal. */
    @Transactional
    public EventoResponse atualizar(Long eventoId, Long organizadorId, EventoRequest requisicao) {
        Evento evento = buscarDoDono(eventoId, organizadorId);
        validarPeriodo(requisicao.dataInicio(), requisicao.dataFim());

        if (evento.getStatus().terminal()) {
            throw new RegraDeNegocioException(
                    "Evento %s nao aceita edicao.".formatted(evento.getStatus()),
                    "EVENTO_NAO_EDITAVEL");
        }

        // Reduzir o limite abaixo das vagas ja confirmadas deixaria inscricoes confirmadas
        // alem do limite, o que quebraria a contagem da RN-01.
        long confirmadas = eventoRepository.contarConfirmadas(eventoId);
        if (requisicao.limiteVagas() < confirmadas) {
            throw new RegraDeNegocioException(
                    "O limite de vagas nao pode ser menor que as %d inscricoes ja confirmadas."
                            .formatted(confirmadas),
                    "LIMITE_ABAIXO_DAS_CONFIRMADAS");
        }

        evento.atualizarDados(requisicao.titulo().trim(), requisicao.descricao().trim(),
                requisicao.local().trim(), requisicao.dataInicio(), requisicao.dataFim(),
                requisicao.cargaHoraria(), requisicao.limiteVagas());

        return EventoResponse.de(evento, confirmadas);
    }

    /** RN-08 e RN-09: transicoes validas, apenas pelo dono. */
    @Transactional
    public EventoResponse mudarStatus(Long eventoId, Long organizadorId,
                                      AtualizarStatusRequest requisicao) {
        Evento evento = buscarDoDono(eventoId, organizadorId);
        StatusEvento destino = requisicao.status();

        if (evento.getStatus() == destino) {
            return EventoResponse.de(evento, eventoRepository.contarConfirmadas(eventoId));
        }
        if (!evento.getStatus().podeTransitarPara(destino)) {
            throw new RegraDeNegocioException(
                    "Transicao de %s para %s nao e permitida.".formatted(evento.getStatus(), destino),
                    "TRANSICAO_INVALIDA");
        }

        StatusEvento origem = evento.getStatus();
        evento.mudarStatus(destino);
        log.info("Evento {} passou de {} para {}", eventoId, origem, destino);
        return EventoResponse.de(evento, eventoRepository.contarConfirmadas(eventoId));
    }

    @Transactional
    public EventoFotoResponse adicionarFoto(Long eventoId, Long organizadorId,
                                            MultipartFile arquivo) {
        Evento evento = buscarDoDono(eventoId, organizadorId);
        String url = armazenamento.salvar(arquivo, "eventos/" + eventoId);

        EventoFoto foto = evento.adicionarFoto(url);
        eventoRepository.flush();
        return EventoFotoResponse.de(foto);
    }

    @Transactional
    public void removerFoto(Long eventoId, Long fotoId, Long organizadorId) {
        Evento evento = buscarDoDono(eventoId, organizadorId);

        String url = evento.getFotos().stream()
                .filter(foto -> foto.getId().equals(fotoId))
                .findFirst()
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", fotoId))
                .getUrl();

        evento.removerFoto(fotoId);
        eventoRepository.flush();
        armazenamento.remover(url);
    }

    /**
     * Carrega o evento garantindo a propriedade do recurso (RN-08). Fica no service de
     * proposito: anotacao de papel nao resolve ownership.
     */
    Evento buscarDoDono(Long eventoId, Long organizadorId) {
        Evento evento = eventoRepository.buscarComFotos(eventoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Evento", eventoId));

        if (!evento.pertenceA(organizadorId)) {
            throw new AcessoNegadoException("Voce nao e o organizador deste evento.");
        }
        return evento;
    }

    /** Espelha a constraint ck_evento_periodo, para o cliente receber 422 em vez de 500. */
    private void validarPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (!fim.isAfter(inicio)) {
            throw new RegraDeNegocioException("A data de fim deve ser posterior a data de inicio.",
                    "PERIODO_INVALIDO");
        }
    }
}
