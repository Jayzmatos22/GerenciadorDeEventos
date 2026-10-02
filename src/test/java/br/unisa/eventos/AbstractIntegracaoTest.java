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

    /** Cada teste comeca com o banco limpo, preservando o schema das migrations. */
    @BeforeEach
    void limparBanco() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE interacao_ia, resumo_avaliacao, avaliacao, certificado, presenca,
                               inscricao, evento_foto, evento, usuario_papel, usuario
                RESTART IDENTITY CASCADE
                """);
    }
}
