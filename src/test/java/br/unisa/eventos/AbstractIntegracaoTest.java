package br.unisa.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import br.unisa.eventos.usuario.Papel;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integracao: Postgres real via Testcontainers, migrations do Flyway
 * aplicadas e MockMvc com a cadeia de seguranca ativa.
 *
 * <p>Requer um daemon Docker disponivel na maquina.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegracaoTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("eventos")
                    .withUsername("eventos")
                    .withPassword("eventos")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Cada teste comeca com o banco limpo, preservando o schema das migrations. */
    @BeforeEach
    void limparBanco() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE interacao_ia, resumo_avaliacao, avaliacao, certificado, presenca,
                               inscricao, evento_foto, evento, usuario_papel, usuario
                RESTART IDENTITY CASCADE
                """);
    }

    protected String json(Object corpo) {
        try {
            return objectMapper.writeValueAsString(corpo);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar o corpo do teste", e);
        }
    }

    /** Registra um usuario e devolve o JWT dele, ja no formato do header Authorization. */
    protected String tokenDe(String email, Papel papel) {
        return "Bearer " + autenticar(email, papel).token();
    }

    protected Autenticado autenticar(String email, Papel papel) {
        try {
            String corpoRegistro = """
                    {"nome":"%s","email":"%s","senha":"senha-de-teste","papel":"%s"}"""
                    .formatted(email.split("@")[0], email, papel.name());

            String resposta = mockMvc.perform(post("/api/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON).content(corpoRegistro))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long id = objectMapper.readTree(resposta).get("id").asLong();

            String login = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","senha":"senha-de-teste"}""".formatted(email)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode corpo = objectMapper.readTree(login);

            return new Autenticado(id, email, corpo.get("token").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao autenticar o usuario de teste " + email, e);
        }
    }

    protected record Autenticado(long id, String email, String token) {

        public String header() {
            return "Bearer " + token;
        }
    }
}
