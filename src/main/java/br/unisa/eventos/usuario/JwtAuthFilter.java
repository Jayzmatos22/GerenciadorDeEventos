package br.unisa.eventos.usuario;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Le o header {@code Authorization: Bearer <jwt>} e popula o SecurityContext.
 * Token ausente ou invalido deixa a requisicao anonima: quem responde 401 e a cadeia de
 * seguranca, no formato padrao de erro.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
                                    FilterChain cadeia) throws ServletException, IOException {
        String header = requisicao.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(PREFIXO)) {
            jwtService.ler(header.substring(PREFIXO.length()).trim())
                    .ifPresent(usuario -> autenticar(usuario, requisicao));
        }

        cadeia.doFilter(requisicao, resposta);
    }

    private void autenticar(UsuarioAutenticado usuario, HttpServletRequest requisicao) {
        var authorities = usuario.papeis().stream()
                .map(papel -> new SimpleGrantedAuthority(papel.authority()))
                .toList();

        var autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(autenticacao);

        // Sessao e STATELESS; nada e persistido entre requisicoes.
        requisicao.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }
}
