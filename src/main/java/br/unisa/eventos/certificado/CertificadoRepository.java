package br.unisa.eventos.certificado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CertificadoRepository extends JpaRepository<Certificado, Long> {

    @Query("""
            select c from Certificado c
            join fetch c.inscricao i
            join fetch i.usuario
            join fetch i.evento
            where c.inscricao.id = :inscricaoId
            """)
    Optional<Certificado> buscarPorInscricao(@Param("inscricaoId") Long inscricaoId);

    @Query("""
            select c from Certificado c
            join fetch c.inscricao i
            join fetch i.usuario
            join fetch i.evento
            where c.codigoAutenticidade = :codigo
            """)
    Optional<Certificado> buscarPorCodigo(@Param("codigo") String codigo);
}
