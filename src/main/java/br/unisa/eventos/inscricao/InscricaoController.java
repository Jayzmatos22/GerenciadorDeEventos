package br.unisa.eventos.inscricao;

import br.unisa.eventos.inscricao.dto.InscricaoResponse;
import br.unisa.eventos.inscricao.dto.InscritoResponse;
import br.unisa.eventos.usuario.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Inscricoes")
public class InscricaoController {

    private final InscricaoService inscricaoService;

    public InscricaoController(InscricaoService inscricaoService) {
        this.inscricaoService = inscricaoService;
    }

    @PostMapping("/api/eventos/{eventoId}/inscricoes")
    @PreAuthorize("hasRole('PARTICIPANTE')")
    @Operation(summary = "Inscreve o participante logado; lota o evento, entra na fila")
    public ResponseEntity<InscricaoResponse> inscrever(
            @PathVariable Long eventoId,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        InscricaoResponse inscricao = inscricaoService.inscrever(usuarioLogado.id(), eventoId);
        return ResponseEntity.status(HttpStatus.CREATED).body(inscricao);
    }

    @DeleteMapping("/api/inscricoes/{id}")
    @Operation(summary = "Cancela a propria inscricao e promove a primeira da fila")
    public ResponseEntity<Void> cancelar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        inscricaoService.cancelar(id, usuarioLogado.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/inscricoes/minhas")
    @Operation(summary = "Historico de inscricoes do usuario logado")
    public Page<InscricaoResponse> minhas(
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
            @PageableDefault(size = 20) Pageable paginacao) {
        return inscricaoService.minhas(usuarioLogado.id(), paginacao);
    }

    @GetMapping("/api/eventos/{eventoId}/inscricoes")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Inscritos e fila de espera do evento, para o organizador dono")
    public Page<InscritoResponse> inscritos(
            @PathVariable Long eventoId,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
            @PageableDefault(size = 50) Pageable paginacao) {
        return inscricaoService.inscritosDoEvento(eventoId, usuarioLogado.id(), paginacao);
    }
}
