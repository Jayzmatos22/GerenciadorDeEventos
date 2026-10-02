package br.unisa.eventos.ia;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InteracaoIARepository extends JpaRepository<InteracaoIA, Long> {

    long countByTipoAndSucesso(TipoInteracaoIA tipo, boolean sucesso);
}
