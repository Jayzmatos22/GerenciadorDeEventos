package br.unisa.eventos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades da aplicacao sob o prefixo {@code app} (secao 10 da especificacao).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        /** Fuso usado por todo {@code LocalDateTime.now()} da aplicacao. */
        String fusoHorario,
        Jwt jwt,
        String urlBase,
        Avaliacao avaliacao,
        Checkin checkin,
        Ia ia,
        Storage storage,
        Cors cors
) {

    public record Jwt(String segredo, long expiracaoHoras) {
    }

    public record Avaliacao(int minimoParaResumo) {
    }

    public record Checkin(long toleranciaHoras) {
    }

    public record Ia(boolean habilitada, long timeoutSegundos) {
    }

    public record Storage(String diretorio) {
    }

    public record Cors(java.util.List<String> origens) {
    }
}
