package br.unisa.eventos.inscricao;

import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.evento.EventoRepository;
import br.unisa.eventos.inscricao.dto.InscricaoResponse;
import br.unisa.eventos.shared.exception.AcessoNegadoException;
import br.unisa.eventos.shared.exception.InscricaoDuplicadaException;
import br.unisa.eventos.shared.exception.RecursoNaoEncontradoException;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import br.unisa.eventos.usuario.Usuario;
import br.unisa.eventos.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Operacoes transacionais de inscricao. Vive separada do {@link InscricaoService} por um
 * motivo pratico: o retry de OptimisticLockException precisa estar fora da transacao, e
 * chamada de metodo no proprio bean nao passa pelo proxy do Spring, logo nao abriria uma
 * transacao nova a cada tentativa.
 */
@Component
class InscricaoTransacional {

    private static final Logger log = LoggerFactory.getLogger(InscricaoTransacional.class);

    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;

    InscricaoTransacional(InscricaoRepository inscricaoRepository,
                          EventoRepository eventoRepository,
                          UsuarioRepository usuarioRepository) {
        this.inscricaoRepository = inscricaoRepository;
        this.eventoRepository = eventoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * RN-01: confirma enquanto houver vaga, senao entra no fim da fila.
     * RN-03: uma unica inscricao ativa por usuario e evento.
     * RN-09: so evento PUBLICADO aceita inscricao.
     *
     * <p>O evento e lido com OPTIMISTIC_FORCE_INCREMENT: duas transacoes disputando a mesma
     * ultima vaga tentam incrementar a mesma versao, e a perdedora falha com
     * OptimisticLockException para ser repetida ja vendo a vaga ocupada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    InscricaoResponse inscrever(Long usuarioId, Long eventoId) {
        Evento evento = eventoRepository.buscarParaConcorrenciaDeVagas(eventoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Evento", eventoId));

        if (!evento.getStatus().aceitaInscricao()) {
            throw new RegraDeNegocioException(
                    "Evento %s nao aceita inscricoes.".formatted(evento.getStatus()),
                    "EVENTO_NAO_ABERTO");
        }
        if (inscricaoRepository.existeAtivaDoUsuarioNoEvento(usuarioId, eventoId)) {
            throw new InscricaoDuplicadaException(
                    "Voce ja possui uma inscricao ativa neste evento.");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", usuarioId));

        long confirmadas = inscricaoRepository.countByEventoIdAndStatus(
                eventoId, StatusInscricao.CONFIRMADA);

        Inscricao inscricao;
        if (confirmadas < evento.getLimiteVagas()) {
            inscricao = Inscricao.confirmada(usuario, evento);
        } else {
            int posicao = inscricaoRepository.ultimaPosicaoDaFila(eventoId) + 1;
            inscricao = Inscricao.emEspera(usuario, evento, posicao);
        }

        Inscricao salva = inscricaoRepository.saveAndFlush(inscricao);
        log.info("Inscricao {} do usuario {} no evento {}: {} (fila {})", salva.getId(),
                usuarioId, eventoId, salva.getStatus(), salva.getPosicaoFila());
        return InscricaoResponse.de(salva);
    }

    /**
     * RN-02: cancelar uma CONFIRMADA promove a primeira da fila na mesma transacao. Cancelar
     * uma EM_ESPERA nao promove ninguem, mas renumera a fila.
     *
     * <p>Tambem incrementa a versao do evento, para serializar com uma inscricao concorrente:
     * sem isso, a vaga liberada poderia ser contada duas vezes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void cancelar(Long inscricaoId, Long usuarioId) {
        Inscricao inscricao = inscricaoRepository.buscarCompleta(inscricaoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Inscricao", inscricaoId));

        if (!inscricao.pertenceA(usuarioId)) {
            throw new AcessoNegadoException("Esta inscricao nao e sua.");
        }
        if (!inscricao.getStatus().ativa()) {
            throw new RegraDeNegocioException(
                    "Inscricao %s nao pode ser cancelada.".formatted(inscricao.getStatus()),
                    "INSCRICAO_NAO_ATIVA");
        }

        Long eventoId = inscricao.getEvento().getId();
        eventoRepository.buscarParaConcorrenciaDeVagas(eventoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Evento", eventoId));

        boolean eraConfirmada = inscricao.getStatus() == StatusInscricao.CONFIRMADA;
        inscricao.cancelar();
        inscricaoRepository.flush();

        List<Inscricao> fila = inscricaoRepository
                .findByEventoIdAndStatusOrderByPosicaoFilaAscIdAsc(eventoId,
                        StatusInscricao.EM_ESPERA);

        if (eraConfirmada && !fila.isEmpty()) {
            Inscricao promovida = fila.removeFirst();
            promovida.promover();
            log.info("Inscricao {} promovida a CONFIRMADA apos cancelamento de {}",
                    promovida.getId(), inscricaoId);
        }

        renumerar(fila);
        inscricaoRepository.flush();
    }

    /** A fila nao tem buracos: quem fica e renumerado de 1 em diante. */
    private void renumerar(List<Inscricao> fila) {
        for (int indice = 0; indice < fila.size(); indice++) {
            fila.get(indice).reposicionarFila(indice + 1);
        }
    }
}
