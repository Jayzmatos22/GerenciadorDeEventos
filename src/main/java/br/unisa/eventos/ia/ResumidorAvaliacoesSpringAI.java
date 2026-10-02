package br.unisa.eventos.ia;

import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.shared.exception.IaIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * Resumo das avaliacoes de um evento via Spring AI. Como no extrator, o
 * {@code ChatClient.Builder} chega por ObjectProvider: sem modelo configurado a chamada falha
 * com IA_INDISPONIVEL em vez de impedir a aplicacao de subir.
 */
@Component
class ResumidorAvaliacoesSpringAI implements ResumidorAvaliacoes {

    private static final Logger log = LoggerFactory.getLogger(ResumidorAvaliacoesSpringAI.class);

    private static final String PROMPT_SISTEMA = """
            Voce resume avaliacoes de eventos academicos para o organizador.

            Regras rigidas:
            - Escreva de 3 a 5 frases, em portugues do Brasil, em texto corrido, sem listas,
              sem titulos e sem markdown.
            - Nao cite nenhum participante pelo nome, nem transcreva comentarios inteiros.
            - Aponte os pontos elogiados e os pontos criticados, nessa ordem, e termine com a
              sugestao de melhoria mais recorrente, se houver alguma.
            - Baseie-se apenas nos comentarios recebidos. Nao invente informacao.
            """;

    private final ObjectProvider<ChatClient.Builder> construtoresDeChatClient;
    private final ExecutorService executor;
    private final Duration timeout;
    private final boolean habilitada;

    ResumidorAvaliacoesSpringAI(ObjectProvider<ChatClient.Builder> construtoresDeChatClient,
                                ExecutorService executorIa, AppProperties propriedades) {
        this.construtoresDeChatClient = construtoresDeChatClient;
        this.executor = executorIa;
        this.timeout = Duration.ofSeconds(propriedades.ia().timeoutSegundos());
        this.habilitada = propriedades.ia().habilitada();
    }

    @Override
    public String resumir(List<String> comentarios) {
        if (!habilitada) {
            throw new IaIndisponivelException("O resumo por IA esta desligado.");
        }

        ChatClient.Builder construtor = construtoresDeChatClient.getIfAvailable();
        if (construtor == null) {
            throw new IaIndisponivelException("Nenhum modelo de IA esta configurado.");
        }

        String resumo = chamarComTimeout(construtor, montarEntrada(comentarios));
        if (!StringUtils.hasText(resumo)) {
            throw new IaIndisponivelException("O modelo devolveu um resumo vazio.");
        }
        return resumo.trim();
    }

    private String montarEntrada(List<String> comentarios) {
        String lista = IntStream.range(0, comentarios.size())
                .mapToObj(indice -> "%d. %s".formatted(indice + 1, comentarios.get(indice)))
                .reduce((anterior, proximo) -> anterior + "\n" + proximo)
                .orElse("");

        return "Comentarios recebidos:\n" + lista;
    }

    private String chamarComTimeout(ChatClient.Builder construtor, String entrada) {
        CompletableFuture<String> chamada = CompletableFuture.supplyAsync(
                () -> construtor.build().prompt()
                        .system(PROMPT_SISTEMA)
                        .user(entrada)
                        .call()
                        .content(),
                executor);

        try {
            return chamada.get(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            chamada.cancel(true);
            throw new IaIndisponivelException(
                    "O modelo nao respondeu em %ds.".formatted(timeout.toSeconds()), e);
        } catch (ExecutionException e) {
            log.warn("Falha ao resumir avaliacoes", e.getCause());
            throw new IaIndisponivelException("O resumo por IA falhou.", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IaIndisponivelException("O resumo por IA foi interrompido.", e);
        }
    }
}
