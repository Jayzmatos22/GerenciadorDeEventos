package br.unisa.eventos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duas armadilhas do perfil {@code openrouter} ja custaram uma investigacao cada, e as duas
 * aparecem no cliente como o mesmo 503 IA_INDISPONIVEL que uma chave ausente daria - entao
 * quem configura culpa a chave.
 *
 * <ul>
 *   <li>A base-url precisa terminar em {@code /v1}: o Spring AI 2.x so concatena
 *       {@code /chat/completions} ao que vier dela.</li>
 *   <li>Os slugs do OpenRouter podem levar sufixo depois de dois-pontos ({@code :free}), que
 *       e tambem o separador de valor padrao do placeholder do Spring. O padrao e tudo que
 *       vem depois do primeiro dois-pontos, entao o sufixo sobrevive.</li>
 * </ul>
 */
@DisplayName("Perfil openrouter - resolucao das propriedades")
class PerfilOpenRouterTest {

    @Test
    @DisplayName("o modelo resolve para um valor utilizavel, sem placeholder por resolver")
    void deveResolverOModelo() throws IOException {
        var ambiente = ambienteComPerfilOpenRouter();

        // De proposito nao se fixa o slug exato: ele e dado que muda, nao invariante. Pinar o
        // valor so travaria a suposicao de quem escreveu - foi o que aconteceu com a base-url,
        // cujo teste passava justamente com a URL errada.
        assertThat(ambiente.getProperty("spring.ai.openai.chat.options.model"))
                .isNotBlank()
                .doesNotContain("${")
                .contains("/");
    }

    @Test
    @DisplayName("a base-url aponta para o OpenRouter e inclui o /v1")
    void deveApontarParaOOpenRouter() throws IOException {
        var ambiente = ambienteComPerfilOpenRouter();

        // O /v1 e obrigatorio: o Spring AI 2.x delega ao SDK oficial da OpenAI, cujo padrao e
        // https://api.openai.com/v1, e que apenas concatena /chat/completions. Sem ele a
        // chamada vira .../api/chat/completions e volta 404 - exatamente o que aconteceu.
        assertThat(ambiente.getProperty("spring.ai.openai.base-url"))
                .isEqualTo("https://openrouter.ai/api/v1")
                .endsWith("/v1");
        assertThat(ambiente.getProperty("spring.ai.model.chat"))
                .as("o OpenRouter fala a API da OpenAI, entao usa o mesmo starter")
                .isEqualTo("openai");
    }

    private StandardEnvironment ambienteComPerfilOpenRouter() throws IOException {
        var ambiente = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("openrouter", new ClassPathResource("application-openrouter.yml"))
                .forEach(fonte -> ambiente.getPropertySources().addFirst(fonte));
        return ambiente;
    }
}
