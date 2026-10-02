package br.unisa.eventos.certificado;

import br.unisa.eventos.certificado.dto.CertificadoResponse;
import br.unisa.eventos.certificado.dto.DadosCertificado;
import br.unisa.eventos.certificado.dto.ValidacaoCertificadoResponse;
import br.unisa.eventos.config.AppProperties;
import br.unisa.eventos.evento.Evento;
import br.unisa.eventos.inscricao.Inscricao;
import br.unisa.eventos.inscricao.InscricaoRepository;
import br.unisa.eventos.presenca.PresencaRepository;
import br.unisa.eventos.shared.exception.AcessoNegadoException;
import br.unisa.eventos.shared.exception.PresencaNaoRegistradaException;
import br.unisa.eventos.shared.exception.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificadoService {

    private static final Logger log = LoggerFactory.getLogger(CertificadoService.class);

    private final CertificadoRepository certificadoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PresencaRepository presencaRepository;
    private final GeradorCertificado gerador;
    private final String urlBase;

    public CertificadoService(CertificadoRepository certificadoRepository,
                              InscricaoRepository inscricaoRepository,
                              PresencaRepository presencaRepository,
                              GeradorCertificado gerador,
                              AppProperties propriedades) {
        this.certificadoRepository = certificadoRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.presencaRepository = presencaRepository;
        this.gerador = gerador;
        this.urlBase = propriedades.urlBase();
    }

    /**
     * RN-05: so emite com presenca registrada, e a emissao e idempotente - chamar duas vezes
     * devolve o mesmo certificado, com o mesmo codigo. E por isso que nao existe um GET de
     * consulta: o POST ja serve para recuperar o certificado existente.
     */
    @Transactional
    public CertificadoResponse emitir(Long inscricaoId, Long usuarioId) {
        Inscricao inscricao = inscricaoDoDono(inscricaoId, usuarioId);

        return certificadoRepository.buscarPorInscricao(inscricaoId)
                .map(existente -> CertificadoResponse.de(existente, urlDeValidacao(existente)))
                .orElseGet(() -> emitirNovo(inscricao));
    }

    /** Gera o PDF sob demanda; o arquivo nao e persistido na v1 (secao 9). */
    @Transactional(readOnly = true)
    public byte[] gerarPdf(Long inscricaoId, Long usuarioId) {
        inscricaoDoDono(inscricaoId, usuarioId);
        return gerador.gerar(dadosDe(certificadoDaInscricao(inscricaoId)));
    }

    /** Validacao publica: confirma o certificado sem expor dados de contato. */
    @Transactional(readOnly = true)
    public ValidacaoCertificadoResponse validar(String codigo) {
        Certificado certificado = certificadoRepository
                .buscarPorCodigo(codigo == null ? "" : codigo.trim().toUpperCase())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhum certificado corresponde a este codigo de autenticidade."));

        Evento evento = certificado.getInscricao().getEvento();
        return new ValidacaoCertificadoResponse(
                certificado.getCodigoAutenticidade(),
                certificado.getInscricao().getUsuario().getNome(),
                evento.getTitulo(), evento.getCargaHoraria(),
                evento.getDataInicio(), evento.getDataFim(), certificado.getDataEmissao());
    }

    private CertificadoResponse emitirNovo(Inscricao inscricao) {
        if (!presencaRepository.existsByInscricaoId(inscricao.getId())) {
            throw new PresencaNaoRegistradaException(
                    "O certificado so pode ser emitido apos o registro de presenca no evento.");
        }

        Certificado certificado = certificadoRepository.save(new Certificado(inscricao));
        log.info("Certificado {} emitido para a inscricao {}", certificado.getId(),
                inscricao.getId());
        return CertificadoResponse.de(certificado, urlDeValidacao(certificado));
    }

    private DadosCertificado dadosDe(Certificado certificado) {
        Inscricao inscricao = certificado.getInscricao();
        Evento evento = inscricao.getEvento();

        return new DadosCertificado(
                inscricao.getUsuario().getNome(), evento.getTitulo(), evento.getLocal(),
                evento.getCargaHoraria(), evento.getDataInicio(), evento.getDataFim(),
                certificado.getDataEmissao(), certificado.getCodigoAutenticidade(),
                urlDeValidacao(certificado));
    }

    private Certificado certificadoDaInscricao(Long inscricaoId) {
        return certificadoRepository.buscarPorInscricao(inscricaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Esta inscricao ainda nao tem certificado emitido."));
    }

    private String urlDeValidacao(Certificado certificado) {
        return "%s/certificados/validar/%s".formatted(urlBase,
                certificado.getCodigoAutenticidade());
    }

    /** O certificado e do participante; nem o organizador baixa o certificado de terceiro. */
    private Inscricao inscricaoDoDono(Long inscricaoId, Long usuarioId) {
        Inscricao inscricao = inscricaoRepository.buscarCompleta(inscricaoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Inscricao", inscricaoId));

        if (!inscricao.pertenceA(usuarioId)) {
            throw new AcessoNegadoException("Esta inscricao nao e sua.");
        }
        return inscricao;
    }
}
