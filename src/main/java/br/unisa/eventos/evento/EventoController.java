package br.unisa.eventos.evento;

import br.unisa.eventos.evento.dto.AtualizarStatusRequest;
import br.unisa.eventos.evento.dto.EventoFotoResponse;
import br.unisa.eventos.evento.dto.EventoRequest;
import br.unisa.eventos.evento.dto.EventoResponse;
import br.unisa.eventos.evento.dto.EventoResumoResponse;
import br.unisa.eventos.ia.AssistenteIA;
import br.unisa.eventos.ia.dto.EventoExtraidoDTO;
import br.unisa.eventos.ia.dto.InterpretarEventoRequest;
import br.unisa.eventos.usuario.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/eventos")
@Tag(name = "Eventos")
public class EventoController {

    private final EventoService eventoService;
    private final AssistenteIA assistenteIA;

    public EventoController(EventoService eventoService, AssistenteIA assistenteIA) {
        this.eventoService = eventoService;
        this.assistenteIA = assistenteIA;
    }

    @GetMapping
    @Operation(summary = "Lista os eventos publicados, com filtros opcionais")
    public Page<EventoResumoResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @PageableDefault(size = 20, sort = "dataInicio") Pageable paginacao) {
        return eventoService.listarPublicados(q, dataInicio, dataFim, paginacao);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe do evento, com fotos e vagas restantes")
    public EventoResponse buscar(@PathVariable Long id) {
        return eventoService.buscar(id);
    }

    @GetMapping("/meus")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Eventos do organizador logado, em qualquer status")
    public Page<EventoResumoResponse> meus(
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
            @PageableDefault(size = 20) Pageable paginacao) {
        return eventoService.listarDoOrganizador(usuarioLogado.id(), paginacao);
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Cria um evento em RASCUNHO")
    public ResponseEntity<EventoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
            @Valid @RequestBody EventoRequest requisicao) {
        EventoResponse criado = eventoService.criar(usuarioLogado.id(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PostMapping("/interpretar")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Extrai os campos de um evento a partir de texto livre;"
            + " nao persiste nada, devolve um rascunho para revisao")
    public EventoExtraidoDTO interpretar(
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
            @Valid @RequestBody InterpretarEventoRequest requisicao) {
        return assistenteIA.interpretarEvento(usuarioLogado.id(), requisicao.texto());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Edita um evento do proprio organizador")
    public EventoResponse atualizar(@PathVariable Long id,
                                    @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                                    @Valid @RequestBody EventoRequest requisicao) {
        return eventoService.atualizar(id, usuarioLogado.id(), requisicao);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Publica, inicia, encerra ou cancela o evento")
    public EventoResponse mudarStatus(@PathVariable Long id,
                                      @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                                      @Valid @RequestBody AtualizarStatusRequest requisicao) {
        return eventoService.mudarStatus(id, usuarioLogado.id(), requisicao);
    }

    @PostMapping("/{id}/fotos")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Envia uma foto para o evento")
    public ResponseEntity<EventoFotoResponse> adicionarFoto(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
            @RequestParam("arquivo") MultipartFile arquivo) {
        EventoFotoResponse foto = eventoService.adicionarFoto(id, usuarioLogado.id(), arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(foto);
    }

    @DeleteMapping("/{id}/fotos/{fotoId}")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @Operation(summary = "Remove uma foto do evento")
    public ResponseEntity<Void> removerFoto(
            @PathVariable Long id, @PathVariable Long fotoId,
            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        eventoService.removerFoto(id, fotoId, usuarioLogado.id());
        return ResponseEntity.noContent().build();
    }
}
