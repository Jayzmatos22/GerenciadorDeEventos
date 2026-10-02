package br.unisa.eventos.presenca;

import br.unisa.eventos.AbstractIntegracaoTest;
import br.unisa.eventos.usuario.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 4 - registro de presenca")
class PresencaIntegracaoTest extends AbstractIntegracaoTest {

    @Test
    @DisplayName("organizador dono registra presenca dentro da janela - RN04")
    void deveRegistrarPresencaNaJanela_RN04() throws Exception {
        Autenticado organizador = autenticar("org-presenca@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("presente@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEmAndamento(organizador, "Em andamento", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        mockMvc.perform(post("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inscricaoId":%d}""".formatted(inscricaoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inscricaoId").value(inscricaoId))
                .andExpect(jsonPath("$.participante.email").value("presente@unisa.br"));
    }

    @Test
    @DisplayName("check-in e aceito dentro da tolerancia apos o fim do evento - RN04")
    void deveAceitarCheckinDentroDaTolerancia_RN04() throws Exception {
        Autenticado organizador = autenticar("org-tolerancia@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("tolerancia@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Terminou ha pouco", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        mockMvc.perform(post("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inscricaoId":%d}""".formatted(inscricaoId)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("check-in antes do inicio do evento e recusado - RN04")
    void deveRecusarCheckinAntesDoInicio_RN04() throws Exception {
        Autenticado organizador = autenticar("org-antes@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("antes@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Ainda vai comecar", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        mockMvc.perform(post("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inscricaoId":%d}""".formatted(inscricaoId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FORA_DA_JANELA_DE_CHECKIN"));
    }

    @Test
    @DisplayName("check-in depois da tolerancia e recusado - RN04")
    void deveRecusarCheckinDepoisDaTolerancia_RN04() throws Exception {
        Autenticado organizador = autenticar("org-tarde@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("tarde@unisa.br", Papel.PARTICIPANTE);
        long eventoId = publicar(organizador, criarEvento(organizador, "Ha muito tempo", 10,
                java.time.LocalDateTime.now().minusDays(10),
                java.time.LocalDateTime.now().minusDays(10).plusHours(4)));
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        mockMvc.perform(post("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inscricaoId":%d}""".formatted(inscricaoId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FORA_DA_JANELA_DE_CHECKIN"));
    }

    @Test
    @DisplayName("organizador que nao e dono nao registra presenca - RN04 e RN08")
    void deveNegarPresencaDeOrganizadorQueNaoEDono_RN04() throws Exception {
        Autenticado dono = autenticar("org-dono-presenca@unisa.br", Papel.ORGANIZADOR);
        Autenticado intruso = autenticar("org-intruso-presenca@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("part-presenca@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEmAndamento(dono, "Protegido", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        mockMvc.perform(post("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inscricaoId":%d}""".formatted(inscricaoId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("participante nao registra a propria presenca - RN04")
    void deveNegarPresencaRegistradaPeloParticipante_RN04() throws Exception {
        Autenticado organizador = autenticar("org-auto@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("auto-checkin@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEmAndamento(organizador, "Sem auto check-in", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        mockMvc.perform(post("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inscricaoId":%d}""".formatted(inscricaoId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("inscricao em espera nao recebe presenca - RN04")
    void deveRecusarPresencaDeInscricaoEmEspera_RN04() throws Exception {
        Autenticado organizador = autenticar("org-espera-presenca@unisa.br", Papel.ORGANIZADOR);
        Autenticado confirmado = autenticar("conf-presenca@unisa.br", Papel.PARTICIPANTE);
        Autenticado naFila = autenticar("fila-presenca@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEmAndamento(organizador, "Vaga unica", 1);
        inscreverConfirmado(confirmado, eventoId);

        String resposta = mockMvc.perform(post("/api/eventos/{id}/inscricoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, naFila.header()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EM_ESPERA"))
                .andReturn().getResponse().getContentAsString();
        long inscricaoEmEspera = objectMapper.readTree(resposta).get("id").asLong();

        mockMvc.perform(post("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inscricaoId":%d}""".formatted(inscricaoEmEspera)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("INSCRICAO_NAO_CONFIRMADA"));
    }

    @Test
    @DisplayName("inscricao de outro evento e recusada")
    void deveRecusarInscricaoDeOutroEvento() throws Exception {
        Autenticado organizador = autenticar("org-cruzado@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("cruzado@unisa.br", Papel.PARTICIPANTE);
        long eventoAlvo = criarEventoEmAndamento(organizador, "Alvo", 10);
        long outroEvento = criarEventoEmAndamento(organizador, "Outro", 10);
        long inscricaoNoOutro = inscreverConfirmado(participante, outroEvento);

        mockMvc.perform(post("/api/eventos/{id}/presencas", eventoAlvo)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inscricaoId":%d}""".formatted(inscricaoNoOutro)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("INSCRICAO_DE_OUTRO_EVENTO"));
    }

    @Test
    @DisplayName("registrar presenca duas vezes e idempotente e nao duplica a linha")
    void deveSerIdempotenteAoRegistrarPresencaDuasVezes() throws Exception {
        Autenticado organizador = autenticar("org-idem@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("idem@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEmAndamento(organizador, "Dupla leitura", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        registrarPresenca(organizador, eventoId, inscricaoId);
        registrarPresenca(organizador, eventoId, inscricaoId);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM presenca WHERE inscricao_id = ?", Long.class, inscricaoId);
        org.assertj.core.api.Assertions.assertThat(total).isEqualTo(1);
    }

    @Test
    @DisplayName("lista de presentes e visivel so para o organizador dono - RN08")
    void deveListarPresentesApenasParaODono_RN08() throws Exception {
        Autenticado dono = autenticar("org-lista-presenca@unisa.br", Papel.ORGANIZADOR);
        Autenticado intruso = autenticar("org-xereta-presenca@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("listado@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEmAndamento(dono, "Lista", 10);
        registrarPresenca(dono, eventoId, inscreverConfirmado(participante, eventoId));

        mockMvc.perform(get("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, dono.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].participante.email").value("listado@unisa.br"));

        mockMvc.perform(get("/api/eventos/{id}/presencas", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header()))
                .andExpect(status().isForbidden());
    }
}
