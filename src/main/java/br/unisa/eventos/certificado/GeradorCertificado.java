package br.unisa.eventos.certificado;

import br.unisa.eventos.certificado.dto.DadosCertificado;

/**
 * Porta de saida da geracao de PDF. Isola a biblioteca escolhida do resto do codigo: o iText
 * Core e AGPL, e se o projeto virar fechado basta uma implementacao nova (secao 2 e 9).
 */
public interface GeradorCertificado {

    byte[] gerar(DadosCertificado dados);
}
