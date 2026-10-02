package br.unisa.eventos.certificado.dto;

import java.time.LocalDateTime;

/**
 * Tudo o que o gerador de PDF precisa saber. Nenhuma entidade JPA cruza a interface
 * {@code GeradorCertificado}, de modo que trocar o iText por OpenPDF ou PDFBox nao toca em
 * mais nada (secao 9 da especificacao).
 */
public record DadosCertificado(
        String nomeParticipante,
        String tituloEvento,
        String localEvento,
        int cargaHoraria,
        LocalDateTime dataInicio,
        LocalDateTime dataFim,
        LocalDateTime dataEmissao,
        String codigoAutenticidade,
        String urlValidacao
) {
}
