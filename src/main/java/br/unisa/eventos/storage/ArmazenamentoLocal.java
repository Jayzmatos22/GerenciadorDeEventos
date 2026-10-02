package br.unisa.eventos.storage;

import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class ArmazenamentoLocal implements ArmazenamentoArquivo {

    static final String PREFIXO_PUBLICO = "/uploads/";

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoLocal.class);
    private static final Set<String> EXTENSOES_ACEITAS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path raiz;

    public ArmazenamentoLocal(AppProperties propriedades) {
        this.raiz = Path.of(propriedades.storage().diretorio()).toAbsolutePath().normalize();
    }

    @Override
    public String salvar(MultipartFile arquivo, String subpasta) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Envie um arquivo de imagem.", "ARQUIVO_INVALIDO");
        }

        String extensao = extensaoDe(arquivo.getOriginalFilename());
        // O nome vem do cliente e nunca e reaproveitado: o arquivo e gravado com um UUID,
        // o que elimina colisao e qualquer tentativa de travessia de diretorio.
        String nome = UUID.randomUUID() + "." + extensao;

        try {
            Path destino = raiz.resolve(subpasta).normalize();
            if (!destino.startsWith(raiz)) {
                throw new RegraDeNegocioException("Caminho de destino invalido.",
                        "ARQUIVO_INVALIDO");
            }
            Files.createDirectories(destino);

            try (InputStream entrada = arquivo.getInputStream()) {
                Files.copy(entrada, destino.resolve(nome), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RegraDeNegocioException("Nao foi possivel gravar o arquivo enviado.",
                    "ARQUIVO_INVALIDO");
        }

        return PREFIXO_PUBLICO + subpasta + "/" + nome;
    }

    @Override
    public void remover(String url) {
        if (url == null || !url.startsWith(PREFIXO_PUBLICO)) {
            return;
        }
        try {
            Path alvo = raiz.resolve(url.substring(PREFIXO_PUBLICO.length())).normalize();
            if (alvo.startsWith(raiz)) {
                Files.deleteIfExists(alvo);
            }
        } catch (IOException e) {
            // O registro no banco ja foi removido; um arquivo orfao em disco nao justifica
            // derrubar a requisicao.
            log.warn("Nao foi possivel remover o arquivo {}", url, e);
        }
    }

    private String extensaoDe(String nomeOriginal) {
        String extensao = StringUtils.getFilenameExtension(
                StringUtils.cleanPath(nomeOriginal == null ? "" : nomeOriginal));
        extensao = extensao == null ? "" : extensao.toLowerCase(Locale.ROOT);

        if (!EXTENSOES_ACEITAS.contains(extensao)) {
            throw new RegraDeNegocioException(
                    "Formato de imagem nao aceito. Use jpg, jpeg, png ou webp.",
                    "ARQUIVO_INVALIDO");
        }
        return extensao;
    }
}
