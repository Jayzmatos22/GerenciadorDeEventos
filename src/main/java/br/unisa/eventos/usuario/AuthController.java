package br.unisa.eventos.usuario;

import br.unisa.eventos.usuario.dto.LoginRequest;
import br.unisa.eventos.usuario.dto.LoginResponse;
import br.unisa.eventos.usuario.dto.RegistrarRequest;
import br.unisa.eventos.usuario.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacao")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registrar")
    @Operation(summary = "Cria um usuario; o papel padrao e PARTICIPANTE")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistrarRequest requisicao) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(requisicao));
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica e devolve o JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest requisicao) {
        return authService.login(requisicao);
    }

    @GetMapping("/eu")
    @Operation(summary = "Dados do usuario logado")
    public UsuarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        return authService.buscarPorId(usuarioLogado.id());
    }
}
