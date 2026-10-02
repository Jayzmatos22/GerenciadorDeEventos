package br.unisa.eventos.avaliacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumoAvaliacaoRepository extends JpaRepository<ResumoAvaliacao, Long> {

    Optional<ResumoAvaliacao> findByEventoId(Long eventoId);
}
