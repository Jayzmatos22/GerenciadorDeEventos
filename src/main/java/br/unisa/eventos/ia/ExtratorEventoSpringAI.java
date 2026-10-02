package br.unisa.eventos.ia;

import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.ia.dto.EventoExtraidoDTO;
import br.unisa.eventos.shared.exception.IaIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Extracao de campos de evento a partir de texto livre, via Spring AI (RF-03).
 *
 * <p>O {@code ChatClient.Builder} chega por {@link ObjectProvider} em vez de injecao direta:
 * no perfil de teste, e sempre que nenhum modelo esta configurado, esse bean nao existe, e a
 * ausencia dele tem que virar IA_INDISPONIVEL em tempo de chamada, nao uma falha na subida do
 * contexto (RN-10).
 */
@Component
class ExtratorEventoSpringAI implements ExtratorEvento {

    private static final Logger log = LoggerFactory.getLogger(ExtratorEventoSpringAI.class);

    private static final String PROMPT_SISTEMA = """
            Voce extrai dados estruturados de eventos academicos a partir de texto livre em
            portugues do Brasil.

            Regras rigidas:
            - Nao invente valores. Se o texto nao disser um campo, devolva null nesse campo.
            - Nao deduza carga horaria a partir das datas, nem limite de vagas a partir do
              tamanho do local. Esses numeros so valem se estiverem escritos no texto.
            - dataInicio e dataFim devem sair no formato ISO-8601 sem fuso, por exemplo
              2026-11-10T19:00:00. O ano corrente e %d; use-o quando o texto citar so dia e mes.
            - cargaHoraria e limiteVagas sao numeros inteiros de horas e de pessoas.
            - descricao e um resumo em uma ou duas frases, tirado do proprio texto.
            """;

    private final ObjectProvider<ChatClient.Builder> construtoresDeChatClient;
    private final ExecutorService executor;
    private final Duration timeout;
    private final boolean habilitada;

    ExtratorEventoSpringAI(ObjectProvider<ChatClient.Builder> construtoresDeChatClient,
                           ExecutorService executorIa, AppProperties propriedades) {
        this.construtoresDeChatClient = construtoresDeChatClient;
        this.executor = executorIa;
        this.timeout = Duration.ofSeconds(propriedades.ia().timeoutSegundos());
        this.habilitada = propriedades.ia().habilitada();
    }

    @Override
    public EventoExtraidoDTO extrair(String texto) {
        if (!habilitada) {
            throw new IaIndisponivelException("A extracao por IA esta desligada.");
        }

        ChatClient.Builder construtor = construtoresDeChatClient.getIfAvailable();
        if (construtor == null) {
            throw new IaIndisponivelException("Nenhum modelo de IA esta configurado.");
        }

        RespostaDoModelo resposta = chamarComTimeout(construtor, texto);
        return montarRascunho(resposta);
    }

    private RespostaDoModelo chamarComTimeout(ChatClient.Builder construtor, String texto) {
        CompletableFuture<RespostaDoModelo> chamada = CompletableFuture.supplyAsync(
                () -> construtor.build().prompt()
                        .system(PROMPT_SISTEMA.formatted(LocalDateTime.now().getYear()))
                        .user(texto)
                        .call()
                        .entity(RespostaDoModelo.class),
                executor);

        try {
            return chamada.get(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            chamada.cancel(true);
            throw new IaIndisponivelException(
                    "O modelo nao respondeu em %ds.".formatted(timeout.toSeconds()), e);
        } catch (ExecutionException e) {
            // Timeout do cliente HTTP, 429, resposta nao parseavel: tudo vira IA_INDISPONIVEL.
            log.warn("Falha na chamada ao modelo de IA", e.getCause());
            throw new IaIndisponivelException("A extracao por IA falhou.", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IaIndisponivelException("A extracao por IA foi interrompida.", e);
        }
    }

    /**
     * // DECISAO: o modelo responde em um record proprio, com as datas em texto, e a conversao
     * para LocalDateTime acontece aqui. A saida estruturada do Spring AI nao tem como garantir
     * o formato de data que o Jackson espera, e uma data mal formatada derrubaria a extracao
     * inteira; assim, o campo problematico cai em camposNaoIdentificados e o organizador
     * preenche na mao.
     *
     * <p>A lista de campos nao identificados e recalculada a partir dos nulos, ignorando o que
     * o modelo tenha dito a respeito: quem decide o que falta e o codigo, nao o modelo.
     */
    private EventoExtraidoDTO montarRascunho(RespostaDoModelo resposta) {
        if (resposta == null) {
            throw new IaIndisponivelException("O modelo devolveu uma resposta vazia.");
        }

        LocalDateTime dataInicio = paraDataHora(resposta.dataInicio());
        LocalDateTime dataFim = paraDataHora(resposta.dataFim());

        List<String> naoIdentificados = new ArrayList<>();
        adicionarSeAusente(naoIdentificados, "titulo", resposta.titulo());
        adicionarSeAusente(naoIdentificados, "descricao", resposta.descricao());
        adicionarSeAusente(naoIdentificados, "local", resposta.local());
        adicionarSeAusente(naoIdentificados, "dataInicio", dataInicio);
        adicionarSeAusente(naoIdentificados, "dataFim", dataFim);
        adicionarSeAusente(naoIdentificados, "cargaHoraria", resposta.cargaHoraria());
        adicionarSeAusente(naoIdentificados, "limiteVagas", resposta.limiteVagas());

        return new EventoExtraidoDTO(
                textoOuNulo(resposta.titulo()), textoOuNulo(resposta.descricao()),
                textoOuNulo(resposta.local()), dataInicio, dataFim,
                resposta.cargaHoraria(), resposta.limiteVagas(), naoIdentificados);
    }

    private void adicionarSeAusente(List<String> destino, String campo, Object valor) {
        boolean ausente = valor == null
                || (valor instanceof String texto && texto.isBlank());
        if (ausente) {
            destino.add(campo);
        }
    }

    private String textoOuNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private LocalDateTime paraDataHora(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.trim().replace(" ", "T");
        try {
            return LocalDateTime.parse(normalizado);
        } catch (DateTimeParseException e) {
            try {
                // O modelo as vezes devolve so a data; nesse caso assumimos o inicio do dia.
                return java.time.LocalDate.parse(normalizado).atStartOfDay();
            } catch (DateTimeParseException tambemFalhou) {
                log.debug("Data em formato nao reconhecido devolvida pelo modelo: {}", valor);
                return null;
            }
        }
    }

    /** Formato pedido ao modelo. Datas como texto, pelos motivos explicados acima. */
    record RespostaDoModelo(
            String titulo,
            String descricao,
            String local,
            String dataInicio,
            String dataFim,
            Integer cargaHoraria,
            Integer limiteVagas
    ) {
    }
}
