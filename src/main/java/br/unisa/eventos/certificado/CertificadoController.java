package br.unisa.eventos.certificado;

import br.unisa.eventos.certificado.dto.CertificadoResponse;
import br.unisa.eventos.certificado.dto.ValidacaoCertificadoResponse;
import br.unisa.eventos.usuario.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Certificado")
public class CertificadoController {

    private final CertificadoService certificadoService;

    public CertificadoController(CertificadoService certificadoService) {
        this.certificadoService = certificadoService;
    }

    @PostMapping("/api/inscricoes/{id}/certificado")
    @Operation(summary = "Emite o certificado da propria inscricao; chamadas repetidas"
            + " devolvem o mesmo certificado")
    public ResponseEntity<CertificadoResponse> emitir(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        CertificadoResponse certificado = certificadoService.emitir(id, usuarioLogado.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(certificado);
    }

    @GetMapping(value = "/api/inscricoes/{id}/certificado", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Baixa o PDF do certificado")
    public ResponseEntity<byte[]> baixar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        byte[] pdf = certificadoService.gerarPdf(id, usuarioLogado.id());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("certificado-%d.pdf".formatted(id)).build().toString())
                .body(pdf);
    }

    @GetMapping("/api/certificados/validar/{codigo}")
    @Operation(summary = "Validacao publica pelo codigo de autenticidade")
    public ValidacaoCertificadoResponse validar(@PathVariable String codigo) {
        return certificadoService.validar(codigo);
    }
}
