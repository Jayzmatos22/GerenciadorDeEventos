package br.unisa.eventos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O perfil groq existe porque a cota dele e da conta, nao capacidade compartilhada como os
 * endpoints {@code :free} do OpenRouter - um 429 ali chega na primeira requisicao e nao ha o
 * que fazer alem de esperar fila de estranhos.
 *
 * <p>Este teste cuida das duas coisas que, erradas, dao o mesmo 503 de chave ausente: a
 * base-url sem {@code /v1} (o SDK so concatena {@code /chat/completions}) e a lista de modelos
 * virando um valor unico em vez de tres.
 */
@DisplayName("Perfil groq - resolucao das propriedades")
class PerfilGroqTest {

    @Test
    @DisplayName("a base-url aponta para o Groq e inclui o /v1")
    void deveApontarParaOGroq() throws IOException {
        var ambiente = ambienteComPerfilGroq();

        assertThat(ambiente.getProperty("spring.ai.openai.base-url"))
                .isEqualTo("https://api.groq.com/openai/v1")
                .endsWith("/v1");
        assertThat(ambiente.getProperty("spring.ai.model.chat"))
                .as("o Groq fala a API da OpenAI, entao usa o mesmo starter")
                .isEqualTo("openai");
    }

    @Test
    @DisplayName("a rotacao vem com mais de um modelo por padrao, sem placeholder por resolver")
    void deveTrazerRotacaoPadrao() throws IOException {
        var ambiente = ambienteComPerfilGroq();

        // O valor default do placeholder e tudo que vem depois do primeiro dois-pontos, e aqui
        // ele carrega virgulas: se o Spring cortasse no lugar errado, a rotacao teria um item.
        String modelos = ambiente.getProperty("app.ia.modelos");
        assertThat(modelos)
                .isNotBlank()
                .doesNotContain("${");
        assertThat(modelos.split(","))
                .as("rotacao com um modelo so nao protege de nada")
                .hasSizeGreaterThan(1);

        assertThat(ambiente.getProperty("spring.ai.openai.chat.options.model"))
                .isNotBlank()
                .doesNotContain("${");
    }

    private StandardEnvironment ambienteComPerfilGroq() throws IOException {
        var ambiente = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("groq", new ClassPathResource("application-groq.yml"))
                .forEach(fonte -> ambiente.getPropertySources().addFirst(fonte));
        return ambiente;
    }
}
