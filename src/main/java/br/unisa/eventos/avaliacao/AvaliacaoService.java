package br.unisa.eventos.avaliacao;

import br.unisa.eventos.avaliacao.dto.AvaliacaoRequest;
import br.unisa.eventos.avaliacao.dto.AvaliacaoResponse;
import br.unisa.eventos.avaliacao.dto.ResumoAvaliacaoResponse;
import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.evento.EventoRepository;
import br.unisa.eventos.ia.AssistenteIA;
import br.unisa.eventos.inscricao.Inscricao;
import br.unisa.eventos.inscricao.InscricaoRepository;
import br.unisa.eventos.presenca.PresencaRepository;
import br.unisa.eventos.shared.exception.AcessoNegadoException;
import br.unisa.eventos.shared.exception.PresencaNaoRegistradaException;
import br.unisa.eventos.shared.exception.RecursoNaoEncontradoException;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AvaliacaoService {

    private static final Logger log = LoggerFactory.getLogger(AvaliacaoService.class);

    private final AvaliacaoRepository avaliacaoRepository;
    private final ResumoAvaliacaoRepository resumoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PresencaRepository presencaRepository;
    private final EventoRepository eventoRepository;
    private final AssistenteIA assistenteIA;
    private final int minimoParaResumo;

    public AvaliacaoService(AvaliacaoRepository avaliacaoRepository,
                            ResumoAvaliacaoRepository resumoRepository,
                            InscricaoRepository inscricaoRepository,
                            PresencaRepository presencaRepository,
                            EventoRepository eventoRepository,
                            AssistenteIA assistenteIA,
                            AppProperties propriedades) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.resumoRepository = resumoRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.presencaRepository = presencaRepository;
        this.eventoRepository = eventoRepository;
        this.assistenteIA = assistenteIA;
        this.minimoParaResumo = propriedades.avaliacao().minimoParaResumo();
    }

    /** RN-06: exige presenca registrada, evento ja terminado e uma avaliacao por inscricao. */
    @Transactional
    public AvaliacaoResponse avaliar(Long inscricaoId, Long usuarioId,
                                     AvaliacaoRequest requisicao) {
        Inscricao inscricao = inscricaoRepository.buscarCompleta(inscricaoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Inscricao", inscricaoId));

        if (!inscricao.pertenceA(usuarioId)) {
            throw new AcessoNegadoException("Esta inscricao nao e sua.");
        }
        if (!presencaRepository.existsByInscricaoId(inscricaoId)) {
            throw new PresencaNaoRegistradaException(
                    "So quem teve presenca registrada pode avaliar o evento.");
        }
        if (inscricao.getEvento().getDataFim().isAfter(LocalDateTime.now())) {
            throw new RegraDeNegocioException("O evento ainda nao terminou.",
                    "EVENTO_NAO_TERMINADO");
        }
        if (avaliacaoRepository.existsByInscricaoId(inscricaoId)) {
            throw new RegraDeNegocioException("Voce ja avaliou este evento.",
                    "AVALIACAO_JA_ENVIADA");
        }

        String comentario = StringUtils.hasText(requisicao.comentario())
                ? requisicao.comentario().trim() : null;

        Avaliacao avaliacao = avaliacaoRepository.save(
                new Avaliacao(inscricao, requisicao.nota(), comentario));
        log.info("Avaliacao {} registrada para a inscricao {} com nota {}", avaliacao.getId(),
                inscricaoId, requisicao.nota());
        return AvaliacaoResponse.de(avaliacao);
    }

    /** RN-08: a lista bruta e do organizador dono do evento. */
    @Transactional(readOnly = true)
    public List<AvaliacaoResponse> listarDoEvento(Long eventoId, Long organizadorId) {
        eventoDoDono(eventoId, organizadorId);
        return avaliacaoRepository.listarDoEvento(eventoId).stream()
                .map(AvaliacaoResponse::de)
                .toList();
    }

    /** RN-07: minimo de avaliacoes configuravel; regerar substitui o resumo anterior. */
    @Transactional
    public ResumoAvaliacaoResponse gerarResumo(Long eventoId, Long organizadorId) {
        Evento evento = eventoDoDono(eventoId, organizadorId);

        long total = avaliacaoRepository.countByInscricaoEventoId(eventoId);
        if (total < minimoParaResumo) {
            throw new RegraDeNegocioException(
                    "O resumo exige no minimo %d avaliacoes; este evento tem %d."
                            .formatted(minimoParaResumo, total),
                    "AVALIACOES_INSUFICIENTES");
        }

        List<String> comentarios = avaliacaoRepository.comentariosDoEvento(eventoId);
        if (comentarios.isEmpty()) {
            throw new RegraDeNegocioException(
                    "As avaliacoes deste evento nao tem comentario para resumir.",
                    "SEM_COMENTARIOS_PARA_RESUMIR");
        }

        String texto = assistenteIA.resumirAvaliacoes(organizadorId, comentarios);
        BigDecimal media = mediaDe(eventoId);
        int totalComoInteiro = (int) total;

        ResumoAvaliacao resumo = resumoRepository.findByEventoId(eventoId)
                .map(existente -> {
                    existente.atualizar(texto, media, totalComoInteiro);
                    return existente;
                })
                .orElseGet(() -> resumoRepository.save(
                        new ResumoAvaliacao(evento, texto, media, totalComoInteiro)));

        log.info("Resumo de avaliacoes do evento {} gerado a partir de {} comentarios",
                eventoId, comentarios.size());
        return ResumoAvaliacaoResponse.de(resumo, eventoId);
    }

    @Transactional(readOnly = true)
    public ResumoAvaliacaoResponse buscarResumo(Long eventoId, Long organizadorId) {
        eventoDoDono(eventoId, organizadorId);

        return resumoRepository.findByEventoId(eventoId)
                .map(resumo -> ResumoAvaliacaoResponse.de(resumo, eventoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Este evento ainda nao tem resumo de avaliacoes gerado."));
    }

    private BigDecimal mediaDe(Long eventoId) {
        return avaliacaoRepository.notaMediaDoEvento(eventoId)
                .map(media -> BigDecimal.valueOf(media).setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private Evento eventoDoDono(Long eventoId, Long organizadorId) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Evento", eventoId));

        if (!evento.pertenceA(organizadorId)) {
            throw new AcessoNegadoException("Voce nao e o organizador deste evento.");
        }
        return evento;
    }
}
