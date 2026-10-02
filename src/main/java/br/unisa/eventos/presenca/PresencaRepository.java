package br.unisa.eventos.presenca;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PresencaRepository extends JpaRepository<Presenca, Long> {

    Optional<Presenca> findByInscricaoId(Long inscricaoId);

    /** RN-05: certificado so sai se existir presenca. */
    boolean existsByInscricaoId(Long inscricaoId);

    @Query("""
            select p from Presenca p
            join fetch p.inscricao i
            join fetch i.usuario
            where i.evento.id = :eventoId
            order by p.dataHoraCheckin asc
            """)
    List<Presenca> listarDoEvento(@Param("eventoId") Long eventoId);
}
