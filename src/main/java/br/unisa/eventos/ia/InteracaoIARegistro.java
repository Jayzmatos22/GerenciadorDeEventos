package br.unisa.eventos.ia;

import br.unisa.eventos.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a trilha de auditoria da IA.
 *
 * <p>Usa REQUIRES_NEW de proposito: quando a chamada ao modelo falha o fluxo levanta
 * IA_INDISPONIVEL, e a transacao de quem chamou pode ser desfeita. O registro da tentativa
 * precisa sobreviver a isso, senao justamente as falhas - o que mais interessa auditar -
 * nunca apareceriam na tabela.
 */
@Component
class InteracaoIARegistro {

    private static final Logger log = LoggerFactory.getLogger(InteracaoIARegistro.class);

    private final InteracaoIARepository repository;
    private final UsuarioRepository usuarioRepository;

    InteracaoIARegistro(InteracaoIARepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void registrar(Long usuarioId, TipoInteracaoIA tipo, String textoEntrada,
                   String jsonExtraido, boolean sucesso) {
        try {
            usuarioRepository.findById(usuarioId).ifPresent(usuario ->
                    repository.save(new InteracaoIA(usuario, tipo, textoEntrada, jsonExtraido,
                            sucesso)));
        } catch (RuntimeException e) {
            // Auditoria nunca derruba o fluxo de negocio.
            log.warn("Nao foi possivel registrar a interacao de IA do tipo {}", tipo, e);
        }
    }
}
