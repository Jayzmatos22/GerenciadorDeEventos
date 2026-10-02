package br.unisa.eventos.certificado.dto;

import br.unisa.eventos.certificado.Certificado;

import java.time.LocalDateTime;

public record CertificadoResponse(
        Long id,
        Long inscricaoId,
        String codigoAutenticidade,
        LocalDateTime dataEmissao,
        String urlValidacao
) {

    public static CertificadoResponse de(Certificado certificado, String urlValidacao) {
        return new CertificadoResponse(certificado.getId(), certificado.getInscricao().getId(),
                certificado.getCodigoAutenticidade(), certificado.getDataEmissao(), urlValidacao);
    }
}
