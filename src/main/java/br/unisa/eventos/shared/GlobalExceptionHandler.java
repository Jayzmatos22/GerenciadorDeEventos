package br.unisa.eventos.shared;

import br.unisa.eventos.shared.dto.ErroResponse;
import br.unisa.eventos.shared.exception.AplicacaoException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

/**
 * Traduz excecoes para o envelope de erro unico da secao 6 da especificacao.
 * Nenhum stacktrace vaza para o cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AplicacaoException.class)
    ResponseEntity<ErroResponse> tratarAplicacao(AplicacaoException e, HttpServletRequest req) {
        log.debug("Erro de negocio {} em {}: {}", e.codigo(), req.getRequestURI(), e.getMessage());
        return resposta(e.status(), e.codigo(), e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroResponse> tratarValidacaoDeCorpo(MethodArgumentNotValidException e,
                                                        HttpServletRequest req) {
        List<ErroResponse.CampoInvalido> campos = e.getBindingResult().getFieldErrors().stream()
                .map(this::paraCampoInvalido)
                .sorted(Comparator.comparing(ErroResponse.CampoInvalido::campo))
                .toList();

        return ResponseEntity.unprocessableEntity().body(ErroResponse.de(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "VALIDACAO",
                "Um ou mais campos estao invalidos.",
                req.getRequestURI(),
                campos));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErroResponse> tratarValidacaoDeParametro(HandlerMethodValidationException e,
                                                            HttpServletRequest req) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDACAO",
                "Um ou mais parametros estao invalidos.", req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErroResponse> tratarRequisicaoMalFormada(Exception e, HttpServletRequest req) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDACAO",
                "Requisicao mal formada.", req);
    }

    /**
     * Rede de seguranca: o indice unico parcial do banco (RN-03) tambem barra inscricao
     * duplicada quando duas requisicoes concorrentes passam pela checagem do service.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErroResponse> tratarIntegridade(DataIntegrityViolationException e,
                                                   HttpServletRequest req) {
        String detalhe = String.valueOf(e.getMostSpecificCause().getMessage());
        if (detalhe.contains("ux_inscricao_ativa")) {
            return resposta(HttpStatus.CONFLICT, "INSCRICAO_DUPLICADA",
                    "Voce ja possui uma inscricao ativa neste evento.", req);
        }
        log.warn("Violacao de integridade em {}: {}", req.getRequestURI(), detalhe);
        return resposta(HttpStatus.CONFLICT, "REGRA_DE_NEGOCIO",
                "A operacao viola uma restricao de integridade dos dados.", req);
    }

    /** Chega aqui quando o @PreAuthorize nega por papel; ownership vem como AcessoNegadoException. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErroResponse> tratarAcessoNegado(AccessDeniedException e, HttpServletRequest req) {
        return resposta(HttpStatus.FORBIDDEN, "ACESSO_NEGADO",
                "Voce nao tem permissao para esta operacao.", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErroResponse> tratarRotaInexistente(NoResourceFoundException e,
                                                       HttpServletRequest req) {
        return resposta(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO",
                "Recurso nao encontrado.", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroResponse> tratarInesperado(Exception e, HttpServletRequest req) {
        log.error("Erro inesperado em {}", req.getRequestURI(), e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Erro interno no servidor.", req);
    }

    private ErroResponse.CampoInvalido paraCampoInvalido(FieldError erro) {
        String mensagem = erro.getDefaultMessage() == null ? "invalido" : erro.getDefaultMessage();
        return new ErroResponse.CampoInvalido(erro.getField(), mensagem);
    }

    private ResponseEntity<ErroResponse> resposta(HttpStatus status, String codigo,
                                                  String mensagem, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ErroResponse.de(status.value(), codigo, mensagem, req.getRequestURI()));
    }
}
