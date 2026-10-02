package br.unisa.eventos.usuario;

import br.unisa.eventos.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Emissao e leitura do JWT HS256 (secao 7 da especificacao). Sem refresh token na v1.
 */
@Service
public class JwtService {

    private static final String CLAIM_NOME = "nome";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_PAPEIS = "papeis";

    private final SecretKey chave;
    private final Duration expiracao;

    public JwtService(AppProperties propriedades) {
        String segredo = propriedades.jwt().segredo();
        if (!StringUtils.hasText(segredo) || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.segredo ausente ou curto: HS256 exige no minimo 32 bytes. "
                            + "Defina a variavel de ambiente JWT_SECRET.");
        }
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracao = Duration.ofHours(propriedades.jwt().expiracaoHoras());
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(expiracao);

        String token = Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim(CLAIM_NOME, usuario.getNome())
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_PAPEIS, usuario.papeis().stream().map(Enum::name).toList())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(chave)
                .compact();

        // O subject e o id, nao o e-mail: o id e estavel mesmo que o e-mail mude.
        return new TokenEmitido(token, LocalDateTime.ofInstant(expiraEm, ZoneId.systemDefault()));
    }

    /**
     * Devolve o principal quando o token e valido e vazio quando nao e. Token invalido nao
     * levanta excecao aqui: o filtro apenas deixa a requisicao sem autenticacao, e a cadeia de
     * seguranca responde 401 no formato padrao de erro.
     */
    public Optional<UsuarioAutenticado> ler(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new UsuarioAutenticado(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_NOME, String.class),
                    claims.get(CLAIM_EMAIL, String.class),
                    papeisDe(claims)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Set<Papel> papeisDe(Claims claims) {
        Object bruto = claims.get(CLAIM_PAPEIS);
        if (!(bruto instanceof List<?> lista)) {
            return Set.of();
        }
        Set<Papel> papeis = new LinkedHashSet<>();
        for (Object item : lista) {
            try {
                papeis.add(Papel.valueOf(String.valueOf(item)));
            } catch (IllegalArgumentException ignorado) {
                // Papel desconhecido em token antigo: ignora em vez de derrubar a requisicao.
            }
        }
        return papeis;
    }

    public record TokenEmitido(String token, LocalDateTime expiraEm) {
    }
}
