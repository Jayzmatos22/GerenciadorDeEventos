package br.unisa.eventos.certificado.dto;

import java.time.LocalDateTime;

/** Resposta publica da validacao: confirma o certificado sem expor dados de contato. */
public record ValidacaoCertificadoResponse(
        String codigoAutenticidade,
        String nomeParticipante,
        String tituloEvento,
        int cargaHoraria,
        LocalDateTime dataInicio,
        LocalDateTime dataFim,
        LocalDateTime dataEmissao
) {
}
