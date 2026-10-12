package br.unisa.eventos.ia;

import com.openai.errors.OpenAIServiceException;

/**
 * Traduz a falha de uma chamada ao modelo para uma linha de log que diga o que aconteceu.
 *
 * <p>// DECISAO: o corpo da resposta de erro entra no log, nao so a mensagem da excecao.
 *
 * <p>O SDK da OpenAI resume o erro em {@code status + message}, e num gateway como o
 * OpenRouter esse resumo nao basta: um 429 chega como {@code "429: Provider returned error"},
 * que e o gateway repassando o erro de quem hospeda o modelo. Pela mensagem nao da para
 * separar "estourei minha cota diaria" de "o provedor do modelo esta saturado agora" -
 * problemas diferentes, com solucoes diferentes. O corpo traz
 * {@code metadata.provider_name} e {@code metadata.raw}, que respondem exatamente isso.
 *
 * <p>O corpo e resposta do servidor, nao pedido: nao carrega a chave de API. Ele so vai para o
 * log; o cliente continua recebendo apenas 503 IA_INDISPONIVEL, como manda a RN-10.
 *
 * <p>A classe do SDK entra por {@code instanceof} e nao esta declarada no {@code pom.xml} de
 * proposito: ela chega pelo {@code spring-ai-starter-model-openai}, que e dependencia fixa do
 * projeto, e declara-la exigiria fixar uma versao a mao - que sairia de sincronia com a que o
 * {@code spring-ai-openai} espera, porque o {@code spring-ai-bom} nao gerencia esse artefato.
 */
final class DiagnosticoFalhaIa {

    private DiagnosticoFalhaIa() {
    }

    /**
     * Se vale a pena tentar o proximo modelo da rotacao depois desta falha.
     *
     * <p>// DECISAO: a lista e de status que NAO vale insistir, e o resto segue adiante.
     *
     * <p>Um 401 (chave invalida) e um 402 (sem credito) sao da conta, nao do modelo: trocar de
     * modelo so gastaria o orcamento de tempo para colecionar o mesmo erro. Ja o 429 ("provider
     * saturado"), o 404 (variante gratuita descontinuada) e os 5xx sao do modelo da vez, e o
     * seguinte pode muito bem responder.
     *
     * <p>Falha que nao vem do servico - JSON que o modelo devolveu fora do formato, timeout do
     * cliente HTTP - tambem segue adiante: um modelo que escreve o raciocinio antes do JSON
     * quebra a leitura, e outro da lista pode nao fazer isso.
     */
    static boolean permiteOutroModelo(Throwable causa) {
        if (causa instanceof OpenAIServiceException erroDoServico) {
            int status = erroDoServico.statusCode();
            return status != 401 && status != 402 && status != 403;
        }
        return true;
    }

    static String descrever(Throwable causa) {
        if (causa == null) {
            return "causa nao informada";
        }
        if (causa instanceof OpenAIServiceException erroDoServico) {
            return "HTTP %d, corpo: %s".formatted(erroDoServico.statusCode(), erroDoServico.body());
        }
        return "%s: %s".formatted(causa.getClass().getSimpleName(), causa.getMessage());
    }
}
