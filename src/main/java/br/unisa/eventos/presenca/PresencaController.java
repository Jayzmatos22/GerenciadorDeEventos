package br.unisa.eventos.presenca;

import br.unisa.eventos.presenca.dto.PresencaResponse;
import br.unisa.eventos.presenca.dto.RegistrarPresencaRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/eventos/{eventoId}/presencas")
@PreAuthorize("hasRole('ORGANIZADOR')")
@Tag(name = "Presenca")
public class PresencaController {

    private final PresencaService presencaService;

    public PresencaController(PresencaService presencaService) {
        this.presencaService = presencaService;
    }

    @PostMapping
    @Operation(summary = "Registra o check-in de uma inscricao confirmada")
    public ResponseEntity<PresencaResponse> registrar(
            @PathVariable Long eventoId,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
            @Valid @RequestBody RegistrarPresencaRequest requisicao) {
        PresencaResponse presenca =
                presencaService.registrar(eventoId, usuarioLogado.id(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(presenca);
    }

    @GetMapping
    @Operation(summary = "Lista os presentes do evento")
    public List<PresencaResponse> listar(
            @PathVariable Long eventoId,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        return presencaService.listarDoEvento(eventoId, usuarioLogado.id());
    }
}
