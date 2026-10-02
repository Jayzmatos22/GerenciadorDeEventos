package br.unisa.eventos.inscricao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InscricaoRepository extends JpaRepository<Inscricao, Long> {

    /** RN-01: vagas ocupadas do evento. */
    long countByEventoIdAndStatus(Long eventoId, StatusInscricao status);

    /** RN-03: ha inscricao ativa do usuario neste evento? */
    @Query("""
            select count(i) > 0 from Inscricao i
            where i.usuario.id = :usuarioId
              and i.evento.id = :eventoId
              and i.status in (br.unisa.eventos.inscricao.StatusInscricao.CONFIRMADA,
                              br.unisa.eventos.inscricao.StatusInscricao.EM_ESPERA)
            """)
    boolean existeAtivaDoUsuarioNoEvento(@Param("usuarioId") Long usuarioId,
                                         @Param("eventoId") Long eventoId);

    /** RN-01: ultima posicao da fila; zero quando a fila esta vazia. */
    @Query("""
            select coalesce(max(i.posicaoFila), 0) from Inscricao i
            where i.evento.id = :eventoId
              and i.status = br.unisa.eventos.inscricao.StatusInscricao.EM_ESPERA
            """)
    int ultimaPosicaoDaFila(@Param("eventoId") Long eventoId);

    /** RN-02: fila do evento em ordem, para promover a primeira e renumerar o resto. */
    List<Inscricao> findByEventoIdAndStatusOrderByPosicaoFilaAscIdAsc(Long eventoId,
                                                                      StatusInscricao status);

    Page<Inscricao> findByUsuarioIdOrderByDataInscricaoDesc(Long usuarioId, Pageable paginacao);

    Page<Inscricao> findByEventoIdOrderByStatusAscPosicaoFilaAscIdAsc(Long eventoId,
                                                                      Pageable paginacao);

    @Query("""
            select i from Inscricao i
            join fetch i.usuario
            join fetch i.evento e
            join fetch e.organizador
            where i.id = :id
            """)
    Optional<Inscricao> buscarCompleta(@Param("id") Long id);
}
