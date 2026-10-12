package br.unisa.eventos.ia;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openai.core.http.Headers;
import com.openai.errors.RateLimitException;
import com.openai.models.ErrorObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Um 429 do OpenRouter chega como {@code "429: Provider returned error"} - mensagem que nao
 * distingue cota propria estourada de provedor saturado. O detalhe esta no corpo da resposta,
 * e este teste existe para que ele nao volte a ficar de fora do log: sem ele a investigacao vai
 * para o lado errado, que foi exatamente o que aconteceu com a base-url sem {@code /v1}.
 */
@DisplayName("Diagnostico de falha da IA")
class DiagnosticoFalhaIaTest {

    @Test
    @DisplayName("erro do servico leva status e corpo, inclusive o metadata do gateway")
    void deveDescreverErroDoServico() throws IOException {
        // Corpo que o OpenRouter devolve num 429 de provedor saturado: nem "code" numerico nem
        // "metadata" existem no contrato da OpenAI, entao sobrevivem como propriedades extras.
        String corpoDoGateway = """
                {"message":"Provider returned error","code":429,
                 "metadata":{"provider_name":"Chutes","raw":"rate limit exceeded"}}
                """;
        // Jackson 2 (com.fasterxml) de proposito, apesar de o Boot 4 usar o 3: o ErrorObject e
        // modelo do SDK da OpenAI, anotado para o 2. Trocar por tools.jackson nao desserializa.
        var erro = RateLimitException.builder()
                .headers(Headers.builder().build())
                .error(JsonMapper.builder().build().readValue(corpoDoGateway, ErrorObject.class))
                .build();

        String descricao = DiagnosticoFalhaIa.descrever(erro);

        assertThat(erro.getMessage())
                .as("a mensagem do SDK por si so nao diz de quem e o limite")
                .isEqualTo("429: Provider returned error");
        assertThat(descricao)
                .contains("HTTP 429")
                .contains("provider_name=Chutes")
                .contains("raw=rate limit exceeded");
    }

    @Test
    @DisplayName("causa que nao vem do servico cai no tipo mais a mensagem")
    void deveDescreverOutrasCausas() {
        String descricao = DiagnosticoFalhaIa.descrever(
                new SocketTimeoutException("Read timed out"));

        assertThat(descricao).isEqualTo("SocketTimeoutException: Read timed out");
    }

    @Test
    @DisplayName("causa ausente nao quebra o log")
    void deveTolerarCausaNula() {
        assertThat(DiagnosticoFalhaIa.descrever(null)).isEqualTo("causa nao informada");
    }
}
