package br.unisa.eventos.avaliacao;

import br.unisa.eventos.avaliacao.dto.AvaliacaoRequest;
import br.unisa.eventos.avaliacao.dto.AvaliacaoResponse;
import br.unisa.eventos.avaliacao.dto.ResumoAvaliacaoResponse;
import br.unisa.eventos.usuario.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Avaliacao")
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    public AvaliacaoController(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    @PostMapping("/api/inscricoes/{id}/avaliacao")
    @Operation(summary = "Avalia o evento da propria inscricao, apos presenca e fim do evento")
    public ResponseEntity<AvaliacaoResponse> avaliar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
            @Valid @RequestBody AvaliacaoRequest requisicao) {
        AvaliacaoResponse avaliacao =
                avaliacaoService.avaliar(id, usuarioLogado.id(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(avaliacao);
    }

    @GetMapping("/api/eventos/{eventoId}/avaliacoes")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Lista bruta das avaliacoes do evento, para o organizador dono")
    public List<AvaliacaoResponse> listar(
            @PathVariable Long eventoId,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        return avaliacaoService.listarDoEvento(eventoId, usuarioLogado.id());
    }

    @PostMapping("/api/eventos/{eventoId}/avaliacoes/resumo")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Gera, ou regera, o resumo das avaliacoes por IA")
    public ResumoAvaliacaoResponse gerarResumo(
            @PathVariable Long eventoId,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        return avaliacaoService.gerarResumo(eventoId, usuarioLogado.id());
    }

    @GetMapping("/api/eventos/{eventoId}/avaliacoes/resumo")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Ultimo resumo gerado para o evento")
    public ResumoAvaliacaoResponse buscarResumo(
            @PathVariable Long eventoId,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        return avaliacaoService.buscarResumo(eventoId, usuarioLogado.id());
    }
}
