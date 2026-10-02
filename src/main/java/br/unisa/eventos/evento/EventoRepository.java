package br.unisa.eventos.evento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventoRepository
        extends JpaRepository<Evento, Long>, JpaSpecificationExecutor<Evento> {

    Page<Evento> findByOrganizadorIdOrderByDataInicioDesc(Long organizadorId, Pageable paginacao);

    @Query("select e from Evento e left join fetch e.fotos where e.id = :id")
    Optional<Evento> buscarComFotos(@Param("id") Long id);

    /**
     * RN-01 e RN-02: le o evento forcando o incremento da versao. Duas transacoes disputando a
     * mesma ultima vaga tentam gravar a mesma versao e a perdedora falha com
     * OptimisticLockException, para ser repetida ja vendo a vaga ocupada.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select e from Evento e where e.id = :id")
    Optional<Evento> buscarParaConcorrenciaDeVagas(@Param("id") Long id);

    /**
     * Vagas ocupadas, para o lado de leitura do evento. E uma consulta nativa de proposito:
     * assim o modulo de evento nao depende do mapeamento de inscricao so para montar a
     * resposta. A contagem que decide confirmacao ou fila (RN-01) e outra, transacional e
     * sob lock, e vive no modulo de inscricao.
     */
    @Query(value = """
            select count(*) from inscricao
            where evento_id = :eventoId and status = 'CONFIRMADA'
            """, nativeQuery = true)
    long contarConfirmadas(@Param("eventoId") Long eventoId);
}
