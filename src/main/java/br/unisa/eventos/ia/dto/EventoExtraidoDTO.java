package br.unisa.eventos.ia.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rascunho devolvido por {@code POST /api/eventos/interpretar}. Nao persiste nada: o
 * organizador revisa, corrige e so entao chama {@code POST /api/eventos} (RF-03).
 *
 * <p>{@code camposNaoIdentificados} lista os campos que o texto nao trazia, para o frontend
 * destacar o que falta preencher.
 */
public record EventoExtraidoDTO(
        String titulo,
        String descricao,
        String local,
        LocalDateTime dataInicio,
        LocalDateTime dataFim,
        Integer cargaHoraria,
        Integer limiteVagas,
        List<String> camposNaoIdentificados
) {
}
