package br.unisa.eventos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 0 - contexto, migrations e health")
class SchemaMigrationTest extends AbstractIntegracaoTest {

    @Test
    @DisplayName("migration V1 cria todas as tabelas da especificacao")
    void deveCriarTodasAsTabelasDoSchema() {
        List<String> tabelas = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tabelas).contains(
                "usuario", "usuario_papel", "evento", "evento_foto", "inscricao",
                "presenca", "certificado", "avaliacao", "resumo_avaliacao", "interacao_ia");
    }

    @Test
    @DisplayName("indice unico parcial de inscricao ativa existe")
    void deveCriarIndiceUnicoParcialDeInscricaoAtiva() {
        String definicao = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_inscricao_ativa'",
                String.class);

        assertThat(definicao).contains("UNIQUE").contains("CANCELADA");
    }

    @Test
    @DisplayName("/actuator/health responde publicamente")
    void deveResponderHealthSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
