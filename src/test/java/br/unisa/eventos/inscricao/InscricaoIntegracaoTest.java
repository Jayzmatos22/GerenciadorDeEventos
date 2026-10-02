package br.unisa.eventos.inscricao;

import br.unisa.eventos.AbstractIntegracaoTest;
import br.unisa.eventos.usuario.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 3 - inscricao, fila de espera e concorrencia")
class InscricaoIntegracaoTest extends AbstractIntegracaoTest {

    @Autowired
    private InscricaoService inscricaoService;

    @Autowired
    private InscricaoRepository inscricaoRepository;

    @Test
    @DisplayName("confirma a inscricao quando ha vaga - RN01")
    void deveConfirmarQuandoHaVaga_RN01() throws Exception {
        Autenticado organizador = autenticar("org-rn01@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("part-rn01@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Com vaga", 2);

        mockMvc.perform(post("/api/eventos/{id}/inscricoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.posicaoFila").doesNotExist())
                .andExpect(jsonPath("$.evento.id").value(eventoId));
    }

    @Test
    @DisplayName("entra na fila quando o evento esta lotado - RN01")
    void deveEntrarNaFilaQuandoEventoLotado_RN01() throws Exception {
        Autenticado organizador = autenticar("org-fila@unisa.br", Papel.ORGANIZADOR);
        Autenticado primeiro = autenticar("primeiro@unisa.br", Papel.PARTICIPANTE);
        Autenticado segundo = autenticar("segundo@unisa.br", Papel.PARTICIPANTE);
        Autenticado terceiro = autenticar("terceiro@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Vaga unica", 1);

        inscrever(primeiro, eventoId, "CONFIRMADA", null);
        inscrever(segundo, eventoId, "EM_ESPERA", 1);
        inscrever(terceiro, eventoId, "EM_ESPERA", 2);
    }

    @Test
    @DisplayName("duas threads disputam a ultima vaga: uma confirma, a outra entra na fila - RN01")
    void deveResolverDisputaPelaUltimaVagaEntreDuasThreads_RN01() throws Exception {
        Autenticado organizador = autenticar("org-concorrencia@unisa.br", Papel.ORGANIZADOR);
        Autenticado candidatoA = autenticar("disputa-a@unisa.br", Papel.PARTICIPANTE);
        Autenticado candidatoB = autenticar("disputa-b@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Ultima vaga", 1);

        // As duas threads esperam na barreira e so entao chamam o service, para que as
        // transacoes se sobreponham de verdade e disputem a mesma versao do evento.
        CyclicBarrier largada = new CyclicBarrier(2);
        Callable<StatusInscricao> tentativa = tentativaDeInscricao(largada, candidatoA, eventoId);
        Callable<StatusInscricao> outraTentativa =
                tentativaDeInscricao(largada, candidatoB, eventoId);

        List<StatusInscricao> resultados;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<StatusInscricao> primeira = executor.submit(tentativa);
            Future<StatusInscricao> segunda = executor.submit(outraTentativa);
            resultados = List.of(primeira.get(), segunda.get());
        }

        assertThat(resultados)
                .as("exatamente uma confirmada e uma em espera")
                .containsExactlyInAnyOrder(StatusInscricao.CONFIRMADA, StatusInscricao.EM_ESPERA);

        assertThat(inscricaoRepository.countByEventoIdAndStatus(eventoId,
                StatusInscricao.CONFIRMADA))
                .as("o limite de uma vaga nao foi estourado")
                .isEqualTo(1);

        Integer posicaoDoEspera = jdbcTemplate.queryForObject("""
                SELECT posicao_fila FROM inscricao
                WHERE evento_id = ? AND status = 'EM_ESPERA'
                """, Integer.class, eventoId);
        assertThat(posicaoDoEspera).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelar uma confirmada promove a primeira da fila - RN02")
    void deveCancelarConfirmadaEPromoverAPrimeiraDaFila_RN02() throws Exception {
        Autenticado organizador = autenticar("org-rn02@unisa.br", Papel.ORGANIZADOR);
        Autenticado confirmado = autenticar("confirmado@unisa.br", Papel.PARTICIPANTE);
        Autenticado naFila = autenticar("na-fila@unisa.br", Papel.PARTICIPANTE);
        Autenticado atrasado = autenticar("atrasado@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Promocao", 1);

        long inscricaoConfirmada = inscrever(confirmado, eventoId, "CONFIRMADA", null);
        long inscricaoNaFila = inscrever(naFila, eventoId, "EM_ESPERA", 1);
        long inscricaoAtrasada = inscrever(atrasado, eventoId, "EM_ESPERA", 2);

        mockMvc.perform(delete("/api/inscricoes/{id}", inscricaoConfirmada)
                        .header(HttpHeaders.AUTHORIZATION, confirmado.header()))
                .andExpect(status().isNoContent());

        assertThat(statusDe(inscricaoNaFila)).isEqualTo("CONFIRMADA");
        assertThat(posicaoFilaDe(inscricaoNaFila)).isNull();
        assertThat(statusDe(inscricaoAtrasada)).isEqualTo("EM_ESPERA");
        assertThat(posicaoFilaDe(inscricaoAtrasada))
                .as("a fila e renumerada, sem buracos")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cancelar uma em espera nao promove ninguem, mas reordena a fila - RN02")
    void deveReordenarFilaAoCancelarEmEspera_RN02() throws Exception {
        Autenticado organizador = autenticar("org-reordena@unisa.br", Papel.ORGANIZADOR);
        Autenticado confirmado = autenticar("conf-reordena@unisa.br", Papel.PARTICIPANTE);
        Autenticado primeiroDaFila = autenticar("fila1@unisa.br", Papel.PARTICIPANTE);
        Autenticado segundoDaFila = autenticar("fila2@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Reordena", 1);

        long inscricaoConfirmada = inscrever(confirmado, eventoId, "CONFIRMADA", null);
        long inscricaoPrimeiro = inscrever(primeiroDaFila, eventoId, "EM_ESPERA", 1);
        long inscricaoSegundo = inscrever(segundoDaFila, eventoId, "EM_ESPERA", 2);

        mockMvc.perform(delete("/api/inscricoes/{id}", inscricaoPrimeiro)
                        .header(HttpHeaders.AUTHORIZATION, primeiroDaFila.header()))
                .andExpect(status().isNoContent());

        assertThat(statusDe(inscricaoConfirmada)).isEqualTo("CONFIRMADA");
        assertThat(statusDe(inscricaoSegundo)).isEqualTo("EM_ESPERA");
        assertThat(posicaoFilaDe(inscricaoSegundo)).isEqualTo(1);
    }

    @Test
    @DisplayName("inscricao ativa duplicada devolve 409 INSCRICAO_DUPLICADA - RN03")
    void deveRecusarInscricaoAtivaDuplicada_RN03() throws Exception {
        Autenticado organizador = autenticar("org-rn03@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("dup-rn03@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Duplicada", 10);

        inscrever(participante, eventoId, "CONFIRMADA", null);

        mockMvc.perform(post("/api/eventos/{id}/inscricoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INSCRICAO_DUPLICADA"));
    }

    @Test
    @DisplayName("reinscricao apos cancelar nao viola o indice unico e entra no fim da fila - RN03")
    void deveReinscreverAposCancelarSemViolarIndiceUnico_RN03() throws Exception {
        Autenticado organizador = autenticar("org-reinscricao@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("reinscreve@unisa.br", Papel.PARTICIPANTE);
        Autenticado outro = autenticar("ocupa-vaga@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Reinscricao", 1);

        long primeira = inscrever(participante, eventoId, "CONFIRMADA", null);

        mockMvc.perform(delete("/api/inscricoes/{id}", primeira)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isNoContent());

        // Outro participante toma a vaga liberada, entao a reinscricao vai para o fim da fila.
        inscrever(outro, eventoId, "CONFIRMADA", null);
        long segunda = inscrever(participante, eventoId, "EM_ESPERA", 1);

        assertThat(segunda).isNotEqualTo(primeira);
        assertThat(statusDe(primeira)).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("evento em RASCUNHO nao aceita inscricao - RN09")
    void deveRecusarInscricaoEmEventoNaoPublicado_RN09() throws Exception {
        Autenticado organizador = autenticar("org-rascunho@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("part-rascunho@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEvento(organizador, "Rascunho", 10,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(2));

        mockMvc.perform(post("/api/eventos/{id}/inscricoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EVENTO_NAO_ABERTO"));
    }

    @Test
    @DisplayName("nao se cancela a inscricao de outra pessoa")
    void deveNegarCancelamentoDeInscricaoAlheia() throws Exception {
        Autenticado organizador = autenticar("org-alheia@unisa.br", Papel.ORGANIZADOR);
        Autenticado dono = autenticar("dono-inscricao@unisa.br", Papel.PARTICIPANTE);
        Autenticado intruso = autenticar("intruso-inscricao@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Alheia", 10);

        long inscricaoId = inscrever(dono, eventoId, "CONFIRMADA", null);

        mockMvc.perform(delete("/api/inscricoes/{id}", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("cancelar duas vezes devolve 422 INSCRICAO_NAO_ATIVA")
    void deveRecusarCancelamentoDeInscricaoJaCancelada() throws Exception {
        Autenticado organizador = autenticar("org-duplo@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("cancela-duplo@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Cancelar duas vezes", 10);
        long inscricaoId = inscrever(participante, eventoId, "CONFIRMADA", null);

        mockMvc.perform(delete("/api/inscricoes/{id}", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/inscricoes/{id}", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("INSCRICAO_NAO_ATIVA"));
    }

    @Test
    @DisplayName("/inscricoes/minhas devolve o historico do participante")
    void deveListarInscricoesDoParticipante() throws Exception {
        Autenticado organizador = autenticar("org-historico@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("historico@unisa.br", Papel.PARTICIPANTE);
        inscrever(participante, criarEventoPublicado(organizador, "Evento A", 5),
                "CONFIRMADA", null);
        inscrever(participante, criarEventoPublicado(organizador, "Evento B", 5),
                "CONFIRMADA", null);

        mockMvc.perform(get("/api/inscricoes/minhas")
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("organizador dono ve inscritos e fila; outro organizador recebe 403 - RN08")
    void deveListarInscritosApenasParaODono_RN08() throws Exception {
        Autenticado dono = autenticar("org-inscritos@unisa.br", Papel.ORGANIZADOR);
        Autenticado intruso = autenticar("org-xereta@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("inscrito@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(dono, "Lista de inscritos", 5);
        inscrever(participante, eventoId, "CONFIRMADA", null);

        mockMvc.perform(get("/api/eventos/{id}/inscricoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, dono.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].participante.email").value("inscrito@unisa.br"));

        mockMvc.perform(get("/api/eventos/{id}/inscricoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("o detalhe publico do evento desconta as vagas ocupadas")
    void deveDescontarVagasOcupadasNoDetalhe() throws Exception {
        Autenticado organizador = autenticar("org-vagas@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("ocupa@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoPublicado(organizador, "Contagem", 3);
        inscrever(participante, eventoId, "CONFIRMADA", null);

        mockMvc.perform(get("/api/eventos/{id}", eventoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasRestantes").value(2));
    }

    private Callable<StatusInscricao> tentativaDeInscricao(CyclicBarrier largada,
                                                           Autenticado candidato, long eventoId) {
        return () -> {
            largada.await();
            return StatusInscricao.valueOf(
                    inscricaoService.inscrever(candidato.id(), eventoId).status().name());
        };
    }

    private long inscrever(Autenticado participante, long eventoId, String statusEsperado,
                           Integer posicaoEsperada) throws Exception {
        String resposta = mockMvc.perform(post("/api/eventos/{id}/inscricoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(statusEsperado))
                .andReturn().getResponse().getContentAsString();

        var corpo = objectMapper.readTree(resposta);
        if (posicaoEsperada == null) {
            assertThat(corpo.hasNonNull("posicaoFila")).isFalse();
        } else {
            assertThat(corpo.get("posicaoFila").asInt()).isEqualTo(posicaoEsperada);
        }
        return corpo.get("id").asLong();
    }

    private String statusDe(long inscricaoId) {
        return jdbcTemplate.queryForObject("SELECT status FROM inscricao WHERE id = ?",
                String.class, inscricaoId);
    }

    private Integer posicaoFilaDe(long inscricaoId) {
        return jdbcTemplate.queryForObject("SELECT posicao_fila FROM inscricao WHERE id = ?",
                Integer.class, inscricaoId);
    }
}
