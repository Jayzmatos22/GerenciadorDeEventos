package br.unisa.eventos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O slug do modelo no OpenRouter termina em {@code :free}, e o separador de valor padrao do
 * Spring tambem e dois-pontos. Num placeholder como
 * {@code ${OPENROUTER_MODEL:meta-llama/llama-3.3-70b-instruct:free}} e facil supor que o
 * sufixo se perca. Este teste fixa a leitura correta: o padrao e tudo que vem depois do
 * primeiro dois-pontos.
 *
 * <p>Errar aqui daria um 503 IA_INDISPONIVEL identico ao de chave ausente, e quem estivesse
 * configurando culparia a chave.
 */
@DisplayName("Perfil openrouter - resolucao das propriedades")
class PerfilOpenRouterTest {

    @Test
    @DisplayName("o sufixo :free do modelo sobrevive ao placeholder")
    void devePreservarOSufixoDoModelo() throws IOException {
        var ambiente = ambienteComPerfilOpenRouter();

        assertThat(ambiente.getProperty("spring.ai.openai.chat.options.model"))
                .isEqualTo("meta-llama/llama-3.3-70b-instruct:free");
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
