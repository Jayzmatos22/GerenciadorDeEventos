package br.unisa.eventos.evento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventoRepository
        extends JpaRepository<Evento, Long>, JpaSpecificationExecutor<Evento> {

    Page<Evento> findByOrganizadorIdOrderByDataInicioDesc(Long organizadorId, Pageable paginacao);

    @Query("select e from Evento e left join fetch e.fotos where e.id = :id")
    Optional<Evento> buscarComFotos(@Param("id") Long id);

    /**
     * Vagas ocupadas para a leitura do evento. Consulta nativa porque o modulo de inscricao
     * nasce na fase 3; a contagem transacional da RN-01 vive no InscricaoService.
     */
    @Query(value = """
            select count(*) from inscricao
            where evento_id = :eventoId and status = 'CONFIRMADA'
            """, nativeQuery = true)
    long contarConfirmadas(@Param("eventoId") Long eventoId);
}
