package br.unisa.eventos.inscricao;

import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.evento.EventoRepository;
import br.unisa.eventos.evento.StatusEvento;
import br.unisa.eventos.shared.exception.AcessoNegadoException;
import br.unisa.eventos.shared.exception.InscricaoDuplicadaException;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import br.unisa.eventos.usuario.Usuario;
import br.unisa.eventos.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras de inscricao isoladas, com repositorios mockados. O comportamento sob concorrencia
 * real esta no teste de integracao, que e o unico lugar onde o lock otimista acontece.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Regras de inscricao - unitario")
class InscricaoTransacionalTest {

    private static final long EVENTO_ID = 1L;
    private static final long USUARIO_ID = 7L;

    @Mock
    private InscricaoRepository inscricaoRepository;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private InscricaoTransacional transacional;

    private Usuario participante;
    private Evento evento;

    @BeforeEach
    void preparar() {
        participante = usuarioComId(USUARIO_ID, "participante@unisa.br");
        evento = eventoPublicado(2);

        when(eventoRepository.buscarParaConcorrenciaDeVagas(EVENTO_ID))
                .thenReturn(Optional.of(evento));
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(participante));
        when(inscricaoRepository.saveAndFlush(any(Inscricao.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @Test
    @DisplayName("confirma enquanto as vagas confirmadas sao menores que o limite - RN01")
    void deveConfirmarQuandoHaVaga_RN01() {
        when(inscricaoRepository.countByEventoIdAndStatus(EVENTO_ID, StatusInscricao.CONFIRMADA))
                .thenReturn(1L);

        var resposta = transacional.inscrever(USUARIO_ID, EVENTO_ID);

        assertThat(resposta.status()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(resposta.posicaoFila()).isNull();
    }

    @Test
    @DisplayName("entra na fila na posicao seguinte a ultima quando lotado - RN01")
    void deveEntrarNaFilaQuandoEventoLotado_RN01() {
        when(inscricaoRepository.countByEventoIdAndStatus(EVENTO_ID, StatusInscricao.CONFIRMADA))
                .thenReturn(2L);
        when(inscricaoRepository.ultimaPosicaoDaFila(EVENTO_ID)).thenReturn(3);

        var resposta = transacional.inscrever(USUARIO_ID, EVENTO_ID);

        assertThat(resposta.status()).isEqualTo(StatusInscricao.EM_ESPERA);
        assertThat(resposta.posicaoFila()).isEqualTo(4);
    }

    @Test
    @DisplayName("a primeira da fila recebe posicao 1 - RN01")
    void devePosicionarPrimeiroDaFilaEmUm_RN01() {
        when(inscricaoRepository.countByEventoIdAndStatus(EVENTO_ID, StatusInscricao.CONFIRMADA))
                .thenReturn(2L);
        when(inscricaoRepository.ultimaPosicaoDaFila(EVENTO_ID)).thenReturn(0);

        assertThat(transacional.inscrever(USUARIO_ID, EVENTO_ID).posicaoFila()).isEqualTo(1);
    }

    @Test
    @DisplayName("recusa segunda inscricao ativa no mesmo evento - RN03")
    void deveRecusarInscricaoAtivaDuplicada_RN03() {
        when(inscricaoRepository.existeAtivaDoUsuarioNoEvento(USUARIO_ID, EVENTO_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> transacional.inscrever(USUARIO_ID, EVENTO_ID))
                .isInstanceOf(InscricaoDuplicadaException.class);

        verify(inscricaoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("recusa inscricao em evento que nao esta PUBLICADO - RN09")
    void deveRecusarInscricaoEmEventoNaoPublicado_RN09() {
        evento.mudarStatus(StatusEvento.ENCERRADO);

        assertThatThrownBy(() -> transacional.inscrever(USUARIO_ID, EVENTO_ID))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting(erro -> ((RegraDeNegocioException) erro).codigo())
                .isEqualTo("EVENTO_NAO_ABERTO");
    }

    @Test
    @DisplayName("cancelar confirmada promove a primeira da fila e renumera o resto - RN02")
    void deveCancelarConfirmadaEPromoverAPrimeiraDaFila_RN02() {
        Inscricao confirmada = inscricaoComId(10L, Inscricao.confirmada(participante, evento));
        Inscricao primeiraDaFila = inscricaoComId(11L,
                Inscricao.emEspera(usuarioComId(8L, "fila1@unisa.br"), evento, 1));
        Inscricao segundaDaFila = inscricaoComId(12L,
                Inscricao.emEspera(usuarioComId(9L, "fila2@unisa.br"), evento, 2));

        when(inscricaoRepository.buscarCompleta(10L)).thenReturn(Optional.of(confirmada));
        when(inscricaoRepository.findByEventoIdAndStatusOrderByPosicaoFilaAscIdAsc(
                EVENTO_ID, StatusInscricao.EM_ESPERA))
                .thenReturn(new ArrayList<>(List.of(primeiraDaFila, segundaDaFila)));

        transacional.cancelar(10L, USUARIO_ID);

        assertThat(confirmada.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        assertThat(primeiraDaFila.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(primeiraDaFila.getPosicaoFila()).isNull();
        assertThat(segundaDaFila.getStatus()).isEqualTo(StatusInscricao.EM_ESPERA);
        assertThat(segundaDaFila.getPosicaoFila()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelar em espera nao promove ninguem - RN02")
    void naoDevePromoverAoCancelarEmEspera_RN02() {
        Inscricao naFila = inscricaoComId(20L, Inscricao.emEspera(participante, evento, 1));
        Inscricao atras = inscricaoComId(21L,
                Inscricao.emEspera(usuarioComId(8L, "atras@unisa.br"), evento, 2));

        when(inscricaoRepository.buscarCompleta(20L)).thenReturn(Optional.of(naFila));
        when(inscricaoRepository.findByEventoIdAndStatusOrderByPosicaoFilaAscIdAsc(
                EVENTO_ID, StatusInscricao.EM_ESPERA))
                .thenReturn(new ArrayList<>(List.of(atras)));

        transacional.cancelar(20L, USUARIO_ID);

        assertThat(naFila.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        assertThat(atras.getStatus()).isEqualTo(StatusInscricao.EM_ESPERA);
        assertThat(atras.getPosicaoFila()).isEqualTo(1);
    }

    @Test
    @DisplayName("nao se cancela inscricao de outra pessoa")
    void deveNegarCancelamentoDeInscricaoAlheia() {
        Inscricao deOutroUsuario = inscricaoComId(30L,
                Inscricao.confirmada(usuarioComId(99L, "outro@unisa.br"), evento));
        when(inscricaoRepository.buscarCompleta(30L)).thenReturn(Optional.of(deOutroUsuario));

        assertThatThrownBy(() -> transacional.cancelar(30L, USUARIO_ID))
                .isInstanceOf(AcessoNegadoException.class);
    }

    private Evento eventoPublicado(int limiteVagas) {
        Evento novo = new Evento(usuarioComId(1L, "org@unisa.br"), "Evento", "Descricao",
                "Auditorio", LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(4), 4, limiteVagas);
        novo.mudarStatus(StatusEvento.PUBLICADO);
        ReflectionTestUtils.setField(novo, "id", EVENTO_ID);
        return novo;
    }

    private Usuario usuarioComId(long id, String email) {
        Usuario usuario = new Usuario(email.split("@")[0], email, "$2a$10$hash");
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    private Inscricao inscricaoComId(long id, Inscricao inscricao) {
        ReflectionTestUtils.setField(inscricao, "id", id);
        return inscricao;
    }
}
