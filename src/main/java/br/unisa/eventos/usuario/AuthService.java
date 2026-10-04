package br.unisa.eventos.usuario;

import br.unisa.eventos.shared.exception.CredenciaisInvalidasException;
import br.unisa.eventos.shared.exception.RecursoNaoEncontradoException;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import br.unisa.eventos.usuario.dto.LoginRequest;
import br.unisa.eventos.usuario.dto.LoginResponse;
import br.unisa.eventos.usuario.dto.RegistrarRequest;
import br.unisa.eventos.usuario.dto.UsuarioResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Hash descartavel, de uma senha que ninguem usa, comparado quando o e-mail nao existe.
     *
     * <p>Sem isso o login responde em milissegundos para e-mail inexistente e em uma centena
     * deles para e-mail cadastrado, porque so no segundo caso o BCrypt roda. A diferenca e
     * grande o bastante para alguem de fora descobrir quem tem conta aqui, sem precisar
     * acertar nenhuma senha.
     */
    private static final String HASH_DESCARTAVEL =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder encoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    /** RN-11: senha com no minimo 8 caracteres (validado no DTO) e hash BCrypt. */
    @Transactional
    public UsuarioResponse registrar(RegistrarRequest requisicao) {
        String email = requisicao.email().trim().toLowerCase();

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraDeNegocioException("Ja existe um usuario com este e-mail.",
                    "EMAIL_EM_USO");
        }

        Usuario usuario = new Usuario(requisicao.nome().trim(), email,
                encoder.encode(requisicao.senha()));
        usuario.adicionarPapel(requisicao.papel() == null ? Papel.PARTICIPANTE : requisicao.papel());

        Usuario salvo = usuarioRepository.save(usuario);
        log.info("Usuario {} registrado com papeis {}", salvo.getId(), salvo.papeis());
        return UsuarioResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest requisicao) {
        Optional<Usuario> encontrado =
                usuarioRepository.findByEmailIgnoreCase(requisicao.email().trim());

        // A comparacao roda nos dois caminhos, inclusive quando o e-mail nao existe, para que
        // o tempo de resposta nao denuncie quem tem conta (ver HASH_DESCARTAVEL).
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(HASH_DESCARTAVEL);
        boolean senhaConfere = encoder.matches(requisicao.senha(), hash);

        Usuario usuario = encontrado
                .filter(ignorado -> senhaConfere)
                .orElseThrow(CredenciaisInvalidasException::new);

        JwtService.TokenEmitido emitido = jwtService.emitir(usuario);
        return new LoginResponse(emitido.token(), emitido.expiraEm(), UsuarioResponse.de(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .map(UsuarioResponse::de)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", id));
    }
}
