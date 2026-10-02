package br.unisa.eventos.inscricao;

import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.evento.EventoRepository;
import br.unisa.eventos.inscricao.dto.InscricaoResponse;
import br.unisa.eventos.inscricao.dto.InscritoResponse;
import br.unisa.eventos.shared.exception.AcessoNegadoException;
import br.unisa.eventos.shared.exception.RecursoNaoEncontradoException;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InscricaoService {

    /** Secao 5 da especificacao: ate 3 tentativas em OptimisticLockException. */
    private static final int MAXIMO_DE_TENTATIVAS = 3;

    private static final Logger log = LoggerFactory.getLogger(InscricaoService.class);

    private final InscricaoTransacional transacional;
    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;

    InscricaoService(InscricaoTransacional transacional, InscricaoRepository inscricaoRepository,
                     EventoRepository eventoRepository) {
        this.transacional = transacional;
        this.inscricaoRepository = inscricaoRepository;
        this.eventoRepository = eventoRepository;
    }

    /** RN-01 e RN-03. O retry mora aqui porque precisa de uma transacao nova por tentativa. */
    public InscricaoResponse inscrever(Long usuarioId, Long eventoId) {
        return comRetry(() -> transacional.inscrever(usuarioId, eventoId),
                "inscrever usuario %d no evento %d".formatted(usuarioId, eventoId));
    }

    /** RN-02. */
    public void cancelar(Long inscricaoId, Long usuarioId) {
        comRetry(() -> {
            transacional.cancelar(inscricaoId, usuarioId);
            return null;
        }, "cancelar a inscricao %d".formatted(inscricaoId));
    }

    @Transactional(readOnly = true)
    public Page<InscricaoResponse> minhas(Long usuarioId, Pageable paginacao) {
        return inscricaoRepository.findByUsuarioIdOrderByDataInscricaoDesc(usuarioId, paginacao)
                .map(InscricaoResponse::de);
    }

    /** RN-08: a lista de inscritos e da propriedade do organizador dono do evento. */
    @Transactional(readOnly = true)
    public Page<InscritoResponse> inscritosDoEvento(Long eventoId, Long organizadorId,
                                                    Pageable paginacao) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Evento", eventoId));

        if (!evento.pertenceA(organizadorId)) {
            throw new AcessoNegadoException("Voce nao e o organizador deste evento.");
        }

        return inscricaoRepository
                .findByEventoIdOrderByStatusAscPosicaoFilaAscIdAsc(eventoId, paginacao)
                .map(InscritoResponse::de);
    }

    private <T> T comRetry(Operacao<T> operacao, String descricao) {
        OptimisticLockingFailureException ultimaFalha = null;

        for (int tentativa = 1; tentativa <= MAXIMO_DE_TENTATIVAS; tentativa++) {
            try {
                return operacao.executar();
            } catch (OptimisticLockingFailureException e) {
                ultimaFalha = e;
                log.debug("Conflito de versao ao {} (tentativa {} de {})",
                        descricao, tentativa, MAXIMO_DE_TENTATIVAS);
            }
        }

        log.warn("Desisti de {} apos {} tentativas", descricao, MAXIMO_DE_TENTATIVAS, ultimaFalha);
        throw new RegraDeNegocioException(
                "O evento esta recebendo muitas inscricoes ao mesmo tempo. Tente novamente.",
                "CONFLITO_DE_CONCORRENCIA");
    }

    @FunctionalInterface
    private interface Operacao<T> {
        T executar();
    }
}
