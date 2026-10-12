package br.unisa.eventos.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rotacao de modelos")
class RotacaoDeModelosTest {

    @Test
    @DisplayName("cada chamada comeca de um ponto diferente e cobre a lista inteira")
    void deveGirarOPontoDePartida() {
        var rotacao = new RotacaoDeModelos(List.of("a", "b", "c"));

        assertThat(rotacao.ordemDaVez()).containsExactly("a", "b", "c");
        assertThat(rotacao.ordemDaVez()).containsExactly("b", "c", "a");
        assertThat(rotacao.ordemDaVez()).containsExactly("c", "a", "b");
        assertThat(rotacao.ordemDaVez())
                .as("da a volta sem repetir nem perder nenhum")
                .containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("espaco em volta, entrada vazia e repetida vem de variavel de ambiente")
    void deveNormalizarAListaConfigurada() {
        // "a, b, , a" e o que sai de IA_MODELOS=a, b, , a - virgula sobrando inclusive.
        var rotacao = new RotacaoDeModelos(Arrays.asList(" a ", "b", "", "   ", null, "a"));

        assertThat(rotacao.quantidade()).isEqualTo(2);
        assertThat(rotacao.ordemDaVez()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("lista vazia ou nula devolve ordem vazia, que significa nao sobrescrever")
    void deveTolerarListaAusente() {
        assertThat(new RotacaoDeModelos(List.of()).ordemDaVez()).isEmpty();
        assertThat(new RotacaoDeModelos(null).ordemDaVez()).isEmpty();
        assertThat(new RotacaoDeModelos(List.of("   ")).ordemDaVez()).isEmpty();
    }

    @Test
    @DisplayName("um modelo so nao deixa de funcionar por existir rotacao")
    void deveFuncionarComUmUnicoModelo() {
        var rotacao = new RotacaoDeModelos(List.of("unico"));

        assertThat(rotacao.ordemDaVez()).containsExactly("unico");
        assertThat(rotacao.ordemDaVez()).containsExactly("unico");
    }

    @Test
    @DisplayName("modelo marcado como indisponivel sai da rotacao")
    void deveTirarDaRotacaoOModeloIndisponivel() {
        var rotacao = new RotacaoDeModelos(List.of("inexistente", "bom", "outro"));

        rotacao.marcarIndisponivel("inexistente");

        assertThat(rotacao.ordemDaVez())
                .as("o 404 nao pode custar uma ida e volta em cada chamada")
                .containsExactlyInAnyOrder("bom", "outro")
                .doesNotContain("inexistente");
        assertThat(rotacao.ordemDaVez()).doesNotContain("inexistente");
    }

    @Test
    @DisplayName("todos marcados: a marca e que esta errada, e a rotacao volta inteira")
    void deveSeRecuperarSeTudoForMarcado() {
        var rotacao = new RotacaoDeModelos(List.of("a", "b"));

        rotacao.marcarIndisponivel("a");
        rotacao.marcarIndisponivel("b");

        // Preferivel tentar todos de novo a deixar a feature desligada ate alguem reiniciar.
        assertThat(rotacao.ordemDaVez()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("marcar modelo nulo ou em branco nao mexe na rotacao")
    void deveIgnorarMarcaInvalida() {
        var rotacao = new RotacaoDeModelos(List.of("a", "b"));

        rotacao.marcarIndisponivel(null);
        rotacao.marcarIndisponivel("   ");

        assertThat(rotacao.ordemDaVez()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("todo modelo recebe a vez ao longo de varias chamadas")
    void deveDistribuirAPrimeiraTentativa() {
        var rotacao = new RotacaoDeModelos(List.of("a", "b", "c", "d"));

        List<String> primeiros = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            primeiros.add(rotacao.ordemDaVez().getFirst());
        }

        assertThat(primeiros).containsExactly("a", "b", "c", "d", "a", "b", "c", "d");
    }
}
