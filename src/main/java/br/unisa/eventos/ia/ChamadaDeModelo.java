package br.unisa.eventos.ia;

import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.shared.exception.IaIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Executa uma chamada ao modelo, percorrendo a rotacao configurada ate uma responder.
 *
 * <p>Extrator e resumidor tinham o mesmo laco de chamada duplicado; a rotacao ficaria duplicada
 * junto, e as duas features se comportariam diferente no dia em que uma fosse ajustada.
 *
 * <p>O {@code ChatClient.Builder} chega por {@link ObjectProvider} em vez de injecao direta:
 * no perfil de teste, e sempre que nenhum modelo esta configurado, esse bean nao existe, e a
 * ausencia dele tem que virar IA_INDISPONIVEL em tempo de chamada, nao uma falha na subida do
 * contexto (RN-10).
 */
@Component
class ChamadaDeModelo {

    private static final Logger log = LoggerFactory.getLogger(ChamadaDeModelo.class);

    private final ObjectProvider<ChatClient.Builder> construtoresDeChatClient;
    private final ExecutorService executor;
    private final Duration orcamento;
    private final RotacaoDeModelos rotacao;

    ChamadaDeModelo(ObjectProvider<ChatClient.Builder> construtoresDeChatClient,
                    ExecutorService executorIa, AppProperties propriedades) {
        this.construtoresDeChatClient = construtoresDeChatClient;
        this.executor = executorIa;
        this.orcamento = Duration.ofSeconds(propriedades.ia().timeoutSegundos());
        this.rotacao = new RotacaoDeModelos(propriedades.ia().modelos());
    }

    /**
     * @param tarefa  sujeito das mensagens de erro, ex. "A extracao por IA"
     * @param acao    monta o prompt sobre o {@code spec} recebido e devolve o resultado; o
     *                modelo da vez ja vem aplicado nele
     */
    <T> T executar(String tarefa, Function<ChatClient.ChatClientRequestSpec, T> acao) {
        ChatClient.Builder construtor = construtoresDeChatClient.getIfAvailable();
        if (construtor == null) {
            throw new IaIndisponivelException("Nenhum modelo de IA esta configurado.");
        }

        // Lista com um nulo quando nada foi configurado: um unico laco atende os dois casos, e
        // o nulo significa "nao sobrescreva o modelo, use o que o Spring AI ja tem".
        List<String> tentativas = rotacao.ordemDaVez();
        if (tentativas.isEmpty()) {
            tentativas = Collections.singletonList(null);
        }

        // // DECISAO: o orcamento de tempo e do pedido inteiro, nao de cada tentativa.
        //
        // Quem espera e o navegador do organizador, e o que ele aguenta nao muda por existirem
        // oito modelos na lista. O 429 de provedor saturado volta em menos de um segundo, sem
        // gerar nada, entao a rotacao cabe de sobra; ja um modelo que aceitou e esta gerando
        // recebe o resto do tempo, porque interromper um que esta funcionando para tentar
        // outro seria trocar resposta por tentativa.
        long prazoFinal = System.nanoTime() + orcamento.toNanos();
        List<String> recusas = new ArrayList<>();
        Throwable ultimaCausa = null;

        for (String modelo : tentativas) {
            long restante = prazoFinal - System.nanoTime();
            if (restante <= 0) {
                break;
            }

            try {
                T resultado = tentarUmModelo(construtor, modelo, acao, restante);
                if (!recusas.isEmpty()) {
                    log.info("{} respondeu com {} depois de {} recusa(s): {}",
                            tarefa, descrever(modelo), recusas.size(), recusas);
                }
                return resultado;
            } catch (TimeoutException e) {
                // Sem tempo sobrando para outro modelo, por construcao: a tentativa recebeu
                // todo o restante do orcamento.
                throw new IaIndisponivelException(
                        "%s nao respondeu em %ds.".formatted(tarefa, orcamento.toSeconds()), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IaIndisponivelException("%s foi interrompida.".formatted(tarefa), e);
            } catch (ExecutionException e) {
                Throwable causa = e.getCause();
                ultimaCausa = causa;
                recusas.add("%s -> %s".formatted(descrever(modelo),
                        DiagnosticoFalhaIa.descrever(causa)));

                if (DiagnosticoFalhaIa.modeloInexistente(causa)) {
                    // Nao e recusa do momento: a conta nao serve este modelo, e insistir
                    // custaria uma ida e volta inutil em cada extracao daqui para frente.
                    rotacao.marcarIndisponivel(modelo);
                    log.warn("{} nao esta disponivel para esta conta e sai da rotacao: {}",
                            descrever(modelo), DiagnosticoFalhaIa.descrever(causa));
                }

                if (!DiagnosticoFalhaIa.permiteOutroModelo(causa)) {
                    log.warn("{} falhou por problema da conta, sem tentar outro modelo: {}",
                            tarefa, recusas, causa);
                    throw new IaIndisponivelException("%s falhou.".formatted(tarefa), causa);
                }
                log.debug("{} recusada por {}; tentando o proximo da rotacao",
                        tarefa, descrever(modelo), causa);
            }
        }

        // A causa que vai na excecao e a da ultima tentativa; as outras ficam no log acima,
        // porque o cliente nao recebe nenhuma delas (RN-10) e o diagnostico mora no log.
        log.warn("{} falhou em {} de {} modelo(s) configurado(s). Recusas: {}",
                tarefa, recusas.size(), Math.max(rotacao.quantidade(), 1), recusas, ultimaCausa);
        throw new IaIndisponivelException("%s falhou.".formatted(tarefa), ultimaCausa);
    }

    private <T> T tentarUmModelo(ChatClient.Builder construtor, String modelo,
                                 Function<ChatClient.ChatClientRequestSpec, T> acao,
                                 long restanteEmNanos)
            throws InterruptedException, ExecutionException, TimeoutException {

        CompletableFuture<T> chamada = CompletableFuture.supplyAsync(
                () -> acao.apply(prepararPrompt(construtor, modelo)), executor);
        try {
            return chamada.get(restanteEmNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            chamada.cancel(true);
            throw e;
        }
    }

    /**
     * O modelo vai como {@link ChatOptions} portavel, e nao como {@code OpenAiChatOptions}: o
     * projeto tem perfis com Ollama e com OpenAI, e opcao de um provedor no outro nao se
     * aplica. O {@code model} e campo da interface portavel, que e tudo que a rotacao precisa.
     */
    private ChatClient.ChatClientRequestSpec prepararPrompt(ChatClient.Builder construtor,
                                                            String modelo) {
        ChatClient.ChatClientRequestSpec spec = construtor.build().prompt();
        return modelo == null ? spec : spec.options(ChatOptions.builder().model(modelo));
    }

    private String descrever(String modelo) {
        return modelo == null ? "o modelo configurado" : modelo;
    }
}
