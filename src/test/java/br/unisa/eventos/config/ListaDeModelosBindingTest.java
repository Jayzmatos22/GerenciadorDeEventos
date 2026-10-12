package br.unisa.eventos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code app.ia.modelos} chega de variavel de ambiente separada por virgula, e o valor padrao e
 * string vazia ({@code ${IA_MODELOS:}}). Esse caso e o que sobe nos perfis de um modelo so, e
 * tem que resultar em lista vazia - nao em lista com um elemento em branco, que faria a rotacao
 * pedir um modelo chamado "".
 */
@DisplayName("Binding da lista de modelos")
class ListaDeModelosBindingTest {

    @Test
    @DisplayName("valor vazio resulta em lista vazia, nao em elemento em branco")
    void deveLerValorVazioComoListaVazia() {
        assertThat(modelosLidosDe("")).isEmpty();
    }

    @Test
    @DisplayName("valores separados por virgula viram uma lista, sem o espaco em volta")
    void deveLerListaSeparadaPorVirgula() {
        assertThat(modelosLidosDe("fornecedor/um:free, fornecedor/dois:free"))
                .containsExactly("fornecedor/um:free", "fornecedor/dois:free");
    }

    private List<String> modelosLidosDe(String valor) {
        var ambiente = new StandardEnvironment();
        ambiente.getPropertySources().addFirst(new MapPropertySource("teste", Map.of(
                "app.fuso-horario", "America/Sao_Paulo",
                "app.ia.habilitada", "true",
                "app.ia.timeout-segundos", "20",
                "app.ia.modelos", valor)));

        return Binder.get(ambiente)
                .bind("app.ia", AppProperties.Ia.class)
                .orElseThrow(() -> new IllegalStateException("app.ia nao ligou"))
                .modelos();
    }
}
