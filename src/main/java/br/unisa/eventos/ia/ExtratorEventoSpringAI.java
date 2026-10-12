package br.unisa.eventos.ia;

import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.ia.dto.EventoExtraidoDTO;
import br.unisa.eventos.shared.exception.IaIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracao de campos de evento a partir de texto livre, via Spring AI (RF-03).
 *
 * <p>Quem chama o modelo, cuida do orcamento de tempo e percorre a rotacao de modelos e a
 * {@link ChamadaDeModelo}; aqui ficam o prompt e a montagem do rascunho.
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

    private final ChamadaDeModelo chamada;
    private final boolean habilitada;

    ExtratorEventoSpringAI(ChamadaDeModelo chamada, AppProperties propriedades) {
        this.chamada = chamada;
        this.habilitada = propriedades.ia().habilitada();
    }

    @Override
    public EventoExtraidoDTO extrair(String texto) {
        if (!habilitada) {
            throw new IaIndisponivelException("A extracao por IA esta desligada.");
        }

        RespostaDoModelo resposta = chamada.executar("A extracao por IA", prompt -> prompt
                .system(PROMPT_SISTEMA.formatted(LocalDateTime.now().getYear()))
                .user(texto)
                .call()
                .entity(RespostaDoModelo.class));

        return montarRascunho(resposta);
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
