package br.unisa.eventos.ia;

import br.unisa.eventos.ia.dto.EventoExtraidoDTO;
import br.unisa.eventos.shared.exception.IaIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Fachada das operacoes de IA. Concentra as duas obrigacoes que valem para toda chamada ao
 * modelo (secao 8 da especificacao): registrar a tentativa em {@code interacao_ia} e traduzir
 * qualquer falha em IA_INDISPONIVEL, sem vazar stacktrace (RN-10).
 */
@Service
public class AssistenteIA {

    private static final Logger log = LoggerFactory.getLogger(AssistenteIA.class);

    private final ExtratorEvento extrator;
    private final InteracaoIARegistro registro;
    private final ObjectMapper objectMapper;

    AssistenteIA(ExtratorEvento extrator, InteracaoIARegistro registro,
                 ObjectMapper objectMapper) {
        this.extrator = extrator;
        this.registro = registro;
        this.objectMapper = objectMapper;
    }

    /** RF-03: devolve um rascunho para revisao humana; nao persiste evento nenhum. */
    public EventoExtraidoDTO interpretarEvento(Long usuarioId, String texto) {
        try {
            EventoExtraidoDTO extraido = extrator.extrair(texto);
            registro.registrar(usuarioId, TipoInteracaoIA.EXTRACAO_EVENTO, texto,
                    paraJson(extraido), true);

            log.info("Extracao de evento concluida para o usuario {}; campos sem valor: {}",
                    usuarioId, extraido.camposNaoIdentificados());
            return extraido;
        } catch (IaIndisponivelException e) {
            registro.registrar(usuarioId, TipoInteracaoIA.EXTRACAO_EVENTO, texto, null, false);
            throw e;
        } catch (RuntimeException e) {
            // RN-10: nenhuma falha da IA escapa como erro inesperado 500.
            registro.registrar(usuarioId, TipoInteracaoIA.EXTRACAO_EVENTO, texto, null, false);
            throw new IaIndisponivelException("A extracao por IA falhou.", e);
        }
    }

    private String paraJson(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JacksonException e) {
            log.debug("Nao foi possivel serializar a saida da IA para auditoria", e);
            return null;
        }
    }
}
