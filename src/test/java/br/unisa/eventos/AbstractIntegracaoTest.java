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

    /** Cria um evento em RASCUNHO pelo organizador informado e devolve o id. */
    protected long criarEvento(Autenticado organizador, String titulo, int limiteVagas,
                               java.time.LocalDateTime inicio, java.time.LocalDateTime fim) {
        try {
            String corpo = """
                    {"titulo":"%s","descricao":"Descricao de %s","local":"Auditorio UNISA",
                     "dataInicio":"%s","dataFim":"%s","cargaHoraria":4,"limiteVagas":%d}"""
                    .formatted(titulo, titulo, inicio, fim, limiteVagas);

            String resposta = mockMvc.perform(post("/api/eventos")
                            .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                    organizador.header())
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            return objectMapper.readTree(resposta).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criar o evento de teste " + titulo, e);
        }
    }

    /** Cria o evento e o publica, deixando-o pronto para receber inscricoes. */
    protected long criarEventoPublicado(Autenticado organizador, String titulo, int limiteVagas) {
        return publicar(organizador, criarEvento(organizador, titulo, limiteVagas,
                java.time.LocalDateTime.now().plusDays(7),
                java.time.LocalDateTime.now().plusDays(7).plusHours(4)));
    }

    protected long publicar(Autenticado organizador, long eventoId) {
        return mudarStatus(organizador, eventoId, "PUBLICADO");
    }

    protected long mudarStatus(Autenticado organizador, long eventoId, String status) {
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .patch("/api/eventos/{id}/status", eventoId)
                            .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                    organizador.header())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"%s"}""".formatted(status)))
                    .andExpect(status().isOk());
            return eventoId;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao mudar o evento para " + status, e);
        }
    }

    /**
     * Inscreve o participante e devolve o id da inscricao. Falha o teste se o status
     * resultante nao for CONFIRMADA.
     */
    protected long inscreverConfirmado(Autenticado participante, long eventoId) {
        try {
            String resposta = mockMvc.perform(post("/api/eventos/{id}/inscricoes", eventoId)
                            .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                    participante.header()))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            JsonNode corpo = objectMapper.readTree(resposta);

            if (!"CONFIRMADA".equals(corpo.get("status").asText())) {
                throw new IllegalStateException("Esperava CONFIRMADA e vi "
                        + corpo.get("status").asText());
            }
            return corpo.get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao inscrever " + participante.email(), e);
        }
    }

    /** Registra presenca pelo organizador dono do evento. */
    protected void registrarPresenca(Autenticado organizador, long eventoId, long inscricaoId) {
        try {
            mockMvc.perform(post("/api/eventos/{id}/presencas", eventoId)
                            .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                    organizador.header())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"inscricaoId":%d}""".formatted(inscricaoId)))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao registrar presenca da inscricao "
                    + inscricaoId, e);
        }
    }

    /**
     * Cria um evento que esta acontecendo agora e ja publicado, de modo que a janela de
     * check-in da RN-04 esteja aberta.
     */
    protected long criarEventoEmAndamento(Autenticado organizador, String titulo,
                                          int limiteVagas) {
        long eventoId = criarEvento(organizador, titulo, limiteVagas,
                java.time.LocalDateTime.now().minusHours(1),
                java.time.LocalDateTime.now().plusHours(3));
        return publicar(organizador, eventoId);
    }

    /** Cria um evento que ja terminou, com a janela de check-in ainda aberta pela tolerancia. */
    protected long criarEventoEncerradoRecentemente(Autenticado organizador, String titulo,
                                                    int limiteVagas) {
        long eventoId = criarEvento(organizador, titulo, limiteVagas,
                java.time.LocalDateTime.now().minusHours(6),
                java.time.LocalDateTime.now().minusHours(2));
        return publicar(organizador, eventoId);
    }

    protected record Autenticado(long id, String email, String token) {

        public String header() {
            return "Bearer " + token;
        }
    }
}
