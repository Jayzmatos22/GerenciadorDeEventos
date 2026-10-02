package br.unisa.eventos.presenca;

import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.evento.EventoRepository;
import br.unisa.eventos.inscricao.Inscricao;
import br.unisa.eventos.inscricao.InscricaoRepository;
import br.unisa.eventos.inscricao.StatusInscricao;
import br.unisa.eventos.presenca.dto.PresencaResponse;
import br.unisa.eventos.presenca.dto.RegistrarPresencaRequest;
import br.unisa.eventos.shared.exception.AcessoNegadoException;
import br.unisa.eventos.shared.exception.RecursoNaoEncontradoException;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import br.unisa.eventos.usuario.Usuario;
import br.unisa.eventos.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PresencaService {

    private static final Logger log = LoggerFactory.getLogger(PresencaService.class);

    private final PresencaRepository presencaRepository;
    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Duration tolerancia;

    public PresencaService(PresencaRepository presencaRepository,
                           InscricaoRepository inscricaoRepository,
                           EventoRepository eventoRepository,
                           UsuarioRepository usuarioRepository,
                           AppProperties propriedades) {
        this.presencaRepository = presencaRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.eventoRepository = eventoRepository;
        this.usuarioRepository = usuarioRepository;
        this.tolerancia = Duration.ofHours(propriedades.checkin().toleranciaHoras());
    }

    /**
     * RN-04: check-in entre dataInicio e dataFim mais a tolerancia, apenas pelo organizador
     * dono do evento e apenas para inscricoes CONFIRMADA.
     *
     * <p>// DECISAO: a operacao e idempotente. Um leitor de QR Code pode ler o mesmo codigo
     * duas vezes, e a segunda leitura devolve a presenca que ja existe em vez de estourar o
     * indice unico de inscricao_id.
     */
    @Transactional
    public PresencaResponse registrar(Long eventoId, Long organizadorId,
                                      RegistrarPresencaRequest requisicao) {
        Evento evento = eventoDoDono(eventoId, organizadorId);
        Inscricao inscricao = inscricaoRepository.buscarCompleta(requisicao.inscricaoId())
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Inscricao",
                        requisicao.inscricaoId()));

        if (!inscricao.getEvento().getId().equals(eventoId)) {
            throw new RegraDeNegocioException("Esta inscricao pertence a outro evento.",
                    "INSCRICAO_DE_OUTRO_EVENTO");
        }
        if (inscricao.getStatus() != StatusInscricao.CONFIRMADA) {
            throw new RegraDeNegocioException(
                    "So inscricao CONFIRMADA recebe presenca; esta esta %s."
                            .formatted(inscricao.getStatus()),
                    "INSCRICAO_NAO_CONFIRMADA");
        }
        validarJanelaDeCheckin(evento);

        return presencaRepository.findByInscricaoId(inscricao.getId())
                .map(PresencaResponse::de)
                .orElseGet(() -> registrarNova(inscricao, organizadorId));
    }

    @Transactional(readOnly = true)
    public List<PresencaResponse> listarDoEvento(Long eventoId, Long organizadorId) {
        eventoDoDono(eventoId, organizadorId);
        return presencaRepository.listarDoEvento(eventoId).stream()
                .map(PresencaResponse::de)
                .toList();
    }

    private PresencaResponse registrarNova(Inscricao inscricao, Long organizadorId) {
        Usuario organizador = usuarioRepository.findById(organizadorId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", organizadorId));

        Presenca presenca = presencaRepository.save(new Presenca(inscricao, organizador));
        log.info("Presenca {} registrada para a inscricao {} pelo organizador {}",
                presenca.getId(), inscricao.getId(), organizadorId);
        return PresencaResponse.de(presenca);
    }

    /** RN-04: a janela vai de dataInicio a dataFim mais app.checkin.tolerancia-horas. */
    private void validarJanelaDeCheckin(Evento evento) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limite = evento.getDataFim().plus(tolerancia);

        if (agora.isBefore(evento.getDataInicio()) || agora.isAfter(limite)) {
            throw new RegraDeNegocioException(
                    "O check-in deste evento so e aceito entre %s e %s."
                            .formatted(evento.getDataInicio(), limite),
                    "FORA_DA_JANELA_DE_CHECKIN");
        }
    }

    /** RN-08: so o organizador criador registra presenca e ve a lista. */
    private Evento eventoDoDono(Long eventoId, Long organizadorId) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Evento", eventoId));

        if (!evento.pertenceA(organizadorId)) {
            throw new AcessoNegadoException("Voce nao e o organizador deste evento.");
        }
        return evento;
    }
}
