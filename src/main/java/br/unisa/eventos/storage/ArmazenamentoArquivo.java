package br.unisa.eventos.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Porta de saida do armazenamento de imagens. A v1 grava em disco local e serve por
 * {@code /uploads/**} (secao 13.3 da especificacao); trocar por S3 ou Cloudinary troca apenas
 * a implementacao.
 */
public interface ArmazenamentoArquivo {

    /**
     * Grava o arquivo e devolve a URL publica relativa.
     *
     * @param subpasta agrupamento logico, por exemplo {@code eventos/12}
     */
    String salvar(MultipartFile arquivo, String subpasta);

    /** Remove o arquivo apontado pela URL devolvida por {@link #salvar}. */
    void remover(String url);
}
