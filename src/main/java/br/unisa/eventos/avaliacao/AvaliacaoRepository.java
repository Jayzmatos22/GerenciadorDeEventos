package br.unisa.eventos.avaliacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    /** RN-06: uma avaliacao por inscricao. */
    boolean existsByInscricaoId(Long inscricaoId);

    @Query("""
            select a from Avaliacao a
            join fetch a.inscricao i
            join fetch i.usuario
            where i.evento.id = :eventoId
            order by a.dataEnvio asc
            """)
    List<Avaliacao> listarDoEvento(@Param("eventoId") Long eventoId);

    @Query("""
            select a.comentario from Avaliacao a
            where a.inscricao.evento.id = :eventoId
              and a.comentario is not null and a.comentario <> ''
            order by a.dataEnvio asc
            """)
    List<String> comentariosDoEvento(@Param("eventoId") Long eventoId);

    @Query("""
            select avg(a.nota) from Avaliacao a
            where a.inscricao.evento.id = :eventoId
            """)
    Optional<Double> notaMediaDoEvento(@Param("eventoId") Long eventoId);

    long countByInscricaoEventoId(Long eventoId);
}
