package br.unisa.eventos.ia;

import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.shared.exception.IaIndisponivelException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Resumo das avaliacoes de um evento via Spring AI. Como no extrator, quem chama o modelo,
 * cuida do orcamento de tempo e percorre a rotacao de modelos e a {@link ChamadaDeModelo}.
 */
@Component
class ResumidorAvaliacoesSpringAI implements ResumidorAvaliacoes {

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

    private final ChamadaDeModelo chamada;
    private final boolean habilitada;

    ResumidorAvaliacoesSpringAI(ChamadaDeModelo chamada, AppProperties propriedades) {
        this.chamada = chamada;
        this.habilitada = propriedades.ia().habilitada();
    }

    @Override
    public String resumir(List<String> comentarios) {
        if (!habilitada) {
            throw new IaIndisponivelException("O resumo por IA esta desligado.");
        }

        String entrada = montarEntrada(comentarios);
        String resumo = chamada.executar("O resumo por IA", prompt -> prompt
                .system(PROMPT_SISTEMA)
                .user(entrada)
                .call()
                .content());
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
}
