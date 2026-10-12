package br.unisa.eventos.ia;

import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.shared.exception.IaIndisponivelException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openai.core.http.Headers;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.ErrorObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * O que este teste protege: um 429 de provedor saturado tem que levar a chamada ao proximo
 * modelo da lista, e um 401 nao - trocar de modelo nao conserta chave invalida, so gasta o
 * orcamento de tempo do pedido.
 *
 * <p>A acao que monta o prompt e um lambda fornecido pelo proprio teste, entao a cadeia do
 * ChatClient nao precisa ser simulada inteira: basta o {@code spec}, que serve para capturar
 * qual modelo cada tentativa pediu.
 */
@DisplayName("Chamada ao modelo com rotacao")
class ChamadaDeModeloTest {

    /** Modelos pedidos, na ordem, lidos do que a implementacao passou para options(). */
    private final List<String> modelosPedidos = new ArrayList<>();

    private ObjectProvider<ChatClient.Builder> construtores;

    @BeforeEach
    void prepararChatClient() {
        var spec = mock(ChatClient.ChatClientRequestSpec.class);
        when(spec.options(any())).thenAnswer(invocacao -> {
            ChatOptions.Builder<?> opcoes = invocacao.getArgument(0);
            modelosPedidos.add(opcoes.build().getModel());
            return spec;
        });

        var cliente = mock(ChatClient.class);
        when(cliente.prompt()).thenReturn(spec);

        var construtor = mock(ChatClient.Builder.class);
        when(construtor.build()).thenReturn(cliente);

        construtores = mock(ObjectProvider.class);
        when(construtores.getIfAvailable()).thenReturn(construtor);
    }

    @Test
    @DisplayName("429 de provedor saturado passa para o proximo modelo da lista")
    void deveSeguirParaOProximoModeloApos429() {
        var chamada = comModelos(List.of("primeiro", "segundo", "terceiro"));
        var tentativas = new ArrayList<String>();

        String resultado = chamada.executar("A extracao por IA", prompt -> {
            tentativas.add("tentou");
            if (tentativas.size() < 3) {
                throw providerSaturado();
            }
            return "extraiu";
        });

        assertThat(resultado).isEqualTo("extraiu");
        assertThat(modelosPedidos)
                .as("as duas recusas nao podem parar na primeira")
                .containsExactly("primeiro", "segundo", "terceiro");
    }

    @Test
    @DisplayName("401 nao tenta outro modelo: chave invalida e da conta, nao do modelo")
    void naoDeveInsistirApos401() {
        var chamada = comModelos(List.of("primeiro", "segundo", "terceiro"));

        assertThatThrownBy(() -> chamada.executar("A extracao por IA", prompt -> {
            throw naoAutorizado();
        }))
                .isInstanceOf(IaIndisponivelException.class)
                .hasMessage("A extracao por IA falhou.");

        assertThat(modelosPedidos).containsExactly("primeiro");
    }

    @Test
    @DisplayName("todos recusando devolve IA_INDISPONIVEL depois de tentar a lista inteira")
    void deveDesistirDepoisDeTentarTodos() {
        var chamada = comModelos(List.of("a", "b"));

        assertThatThrownBy(() -> chamada.executar("O resumo por IA", prompt -> {
            throw providerSaturado();
        }))
                .isInstanceOf(IaIndisponivelException.class)
                .hasMessage("O resumo por IA falhou.");

        assertThat(modelosPedidos).containsExactly("a", "b");
    }

    @Test
    @DisplayName("JSON fora do formato tambem passa para o proximo: outro modelo pode acertar")
    void deveSeguirAposRespostaIlegivel() {
        var chamada = comModelos(List.of("tagarela", "obediente"));
        var tentativas = new ArrayList<String>();

        String resultado = chamada.executar("A extracao por IA", prompt -> {
            tentativas.add("tentou");
            if (tentativas.size() == 1) {
                // O que um modelo de raciocinio causa: escreve antes do JSON e a leitura falha.
                throw new IllegalStateException("Nao foi possivel converter a resposta");
            }
            return "extraiu";
        });

        assertThat(resultado).isEqualTo("extraiu");
        assertThat(modelosPedidos).containsExactly("tagarela", "obediente");
    }

    @Test
    @DisplayName("404 tira o modelo da rotacao: a segunda chamada nao tenta de novo")
    void deveDeixarDeTentarModeloInexistente() {
        // O caso real: llama-3.3-70b-versatile existe no catalogo do Groq mas nao na conta,
        // e sem memoria a rotacao pagaria o 404 em toda extracao.
        var chamada = comModelos(List.of("inexistente", "bom"));

        String primeira = chamada.executar("A extracao por IA", prompt -> {
            if (modelosPedidos.getLast().equals("inexistente")) {
                throw modeloNaoEncontrado();
            }
            return "extraiu";
        });

        assertThat(primeira).isEqualTo("extraiu");
        assertThat(modelosPedidos).containsExactly("inexistente", "bom");

        modelosPedidos.clear();
        String segunda = chamada.executar("A extracao por IA", prompt -> "extraiu");

        assertThat(segunda).isEqualTo("extraiu");
        assertThat(modelosPedidos)
                .as("o modelo que a conta nao serve nao volta para a rotacao")
                .containsExactly("bom");
    }

    @Test
    @DisplayName("sem lista configurada faz uma tentativa, sem sobrescrever o modelo do perfil")
    void deveRespeitarOPerfilQuandoNaoHaLista() {
        var chamada = comModelos(List.of());

        String resultado = chamada.executar("A extracao por IA", prompt -> "extraiu");

        assertThat(resultado).isEqualTo("extraiu");
        assertThat(modelosPedidos)
                .as("options() nem deve ser chamado: o modelo e o que o spring.ai configurou")
                .isEmpty();
    }

    @Test
    @DisplayName("sem ChatClient no contexto a falha e de chamada, nao de subida - RN10")
    void deveFalharEmTempoDeChamadaSemModeloConfigurado() {
        ObjectProvider<ChatClient.Builder> vazio = mock(ObjectProvider.class);
        when(vazio.getIfAvailable()).thenReturn(null);

        var chamada = new ChamadaDeModelo(vazio, Executors.newVirtualThreadPerTaskExecutor(),
                propriedades(List.of()));

        assertThatThrownBy(() -> chamada.executar("A extracao por IA", prompt -> "nunca"))
                .isInstanceOf(IaIndisponivelException.class)
                .hasMessage("Nenhum modelo de IA esta configurado.");
    }

    private ChamadaDeModelo comModelos(List<String> modelos) {
        return new ChamadaDeModelo(construtores, Executors.newVirtualThreadPerTaskExecutor(),
                propriedades(modelos));
    }

    private AppProperties propriedades(List<String> modelos) {
        return new AppProperties("America/Sao_Paulo", null, null, null, null,
                new AppProperties.Ia(true, 20, modelos), null, null);
    }

    /** 429 como o OpenRouter devolve: o provedor do modelo recusou, nao a conta. */
    private OpenAIServiceException providerSaturado() {
        return RateLimitException.builder()
                .headers(Headers.builder().build())
                .error(corpoDeErro("""
                        {"message":"Provider returned error","code":429,
                         "metadata":{"provider_name":"Chutes","raw":"upstream recusou"}}
                        """))
                .build();
    }

    /** 404 como o Groq devolve para um modelo fora do catalogo da conta. */
    private OpenAIServiceException modeloNaoEncontrado() {
        return NotFoundException.builder()
                .headers(Headers.builder().build())
                .error(corpoDeErro("""
                        {"code":"model_not_found","type":"invalid_request_error",
                         "message":"The model `inexistente` does not exist or you do not have access to it."}
                        """))
                .build();
    }

    /** 401: problema da chave, que nenhum outro modelo da lista resolve. */
    private OpenAIServiceException naoAutorizado() {
        return UnauthorizedException.builder()
                .headers(Headers.builder().build())
                .error(corpoDeErro("""
                        {"message":"No auth credentials found","code":401}
                        """))
                .build();
    }

    private ErrorObject corpoDeErro(String json) {
        try {
            // Jackson 2 (com.fasterxml) de proposito, apesar de o Boot 4 usar o 3: ErrorObject
            // e modelo do SDK da OpenAI, anotado para o 2.
            return JsonMapper.builder().build().readValue(json, ErrorObject.class);
        } catch (IOException e) {
            throw new IllegalStateException("JSON de teste invalido", e);
        }
    }
}
