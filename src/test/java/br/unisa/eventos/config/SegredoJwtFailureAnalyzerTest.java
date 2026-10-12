package br.unisa.eventos.config;

import br.unisa.eventos.usuario.JwtService;
import br.unisa.eventos.usuario.SegredoJwtInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.diagnostics.FailureAnalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Segredo do JWT ausente - diagnostico de subida")
class SegredoJwtFailureAnalyzerTest {

    @ParameterizedTest(name = "segredo \"{0}\"")
    @ValueSource(strings = {"", "   ", "curto-demais", "31-bytes-ainda-insuficientes!!"})
    @DisplayName("segredo vazio ou curto impede a subida, com excecao reconhecivel")
    void deveRecusarSegredoVazioOuCurto(String segredo) {
        assertThatThrownBy(() -> new JwtService(propriedadesCom(segredo)))
                .isInstanceOf(SegredoJwtInvalidoException.class);
    }

    @Test
    @DisplayName("segredo com 32 bytes ou mais e aceito")
    void deveAceitarSegredoDeTamanhoSuficiente() {
        assertThat(new JwtService(propriedadesCom("a".repeat(32)))).isNotNull();
    }

    @Test
    @DisplayName("o diagnostico explica a causa e aponta os dois caminhos de solucao")
    void deveExplicarComoResolver() {
        var analisador = new SegredoJwtFailureAnalyzer();
        var causa = new SegredoJwtInvalidoException("segredo ausente");

        FailureAnalysis analise = analisador.analyze(causa, causa);

        assertThat(analise).isNotNull();
        assertThat(analise.getDescription()).isEqualTo("segredo ausente");
        assertThat(analise.getAction())
                .contains("spring-boot.run.profiles=dev")
                .contains("SPRING_PROFILES_ACTIVE=dev")
                .contains("JWT_SECRET");
    }

    private AppProperties propriedadesCom(String segredo) {
        return new AppProperties(
                "America/Sao_Paulo",
                new AppProperties.Jwt(segredo, 8),
                "http://localhost:8080",
                new AppProperties.Avaliacao(3),
                new AppProperties.Checkin(24),
                new AppProperties.Ia(false, 20, java.util.List.of()),
                new AppProperties.Storage("uploads"),
                new AppProperties.Cors(java.util.List.of()));
    }
}
