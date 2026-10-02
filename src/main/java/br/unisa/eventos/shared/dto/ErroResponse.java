package br.unisa.eventos.shared.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resposta unica para todo erro tratado (secao 6 da especificacao).
 */
public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String codigo,
        String mensagem,
        String caminho,
        List<CampoInvalido> campos
) {

    public record CampoInvalido(String campo, String erro) {
    }

    public static ErroResponse de(int status, String codigo, String mensagem, String caminho) {
        return new ErroResponse(LocalDateTime.now(), status, codigo, mensagem, caminho, null);
    }

    public static ErroResponse de(int status, String codigo, String mensagem, String caminho,
                                  List<CampoInvalido> campos) {
        return new ErroResponse(LocalDateTime.now(), status, codigo, mensagem, caminho, campos);
    }
}
