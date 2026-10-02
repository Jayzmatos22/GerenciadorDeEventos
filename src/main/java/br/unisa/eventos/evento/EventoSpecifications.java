package br.unisa.eventos.evento;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Filtros do catalogo publico (RF-04).
 *
 * <p>Monta os predicados com a Criteria API em vez de JPQL com {@code :param is null}: o
 * Postgres nao consegue inferir o tipo de um parametro que chega nulo e recusa a consulta.
 * Aqui o filtro ausente devolve {@link Specification#unrestricted()} e nao entra no WHERE.
 */
final class EventoSpecifications {

    private EventoSpecifications() {
    }

    static Specification<Evento> comStatus(StatusEvento status) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get("status"), status);
    }

    /** Busca o termo em titulo, descricao e local, sem diferenciar maiusculas. */
    static Specification<Evento> contendoTermo(String termo) {
        if (termo == null || termo.isBlank()) {
            return Specification.unrestricted();
        }
        String padrao = "%" + termo.trim().toLowerCase(Locale.ROOT) + "%";

        return (raiz, consulta, cb) -> {
            Predicate noTitulo = cb.like(cb.lower(raiz.get("titulo")), padrao);
            Predicate naDescricao = cb.like(cb.lower(raiz.get("descricao")), padrao);
            Predicate noLocal = cb.like(cb.lower(raiz.get("local")), padrao);
            return cb.or(noTitulo, naDescricao, noLocal);
        };
    }

    static Specification<Evento> comecandoApos(LocalDateTime de) {
        return de == null ? Specification.unrestricted()
                : (raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("dataInicio"), de);
    }

    static Specification<Evento> comecandoAntes(LocalDateTime ate) {
        return ate == null ? Specification.unrestricted()
                : (raiz, consulta, cb) -> cb.lessThanOrEqualTo(raiz.get("dataInicio"), ate);
    }
}
