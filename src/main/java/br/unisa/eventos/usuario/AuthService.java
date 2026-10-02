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

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(requisicao.email().trim())
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!encoder.matches(requisicao.senha(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

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
