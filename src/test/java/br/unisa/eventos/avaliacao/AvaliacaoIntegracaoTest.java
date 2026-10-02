package br.unisa.eventos.avaliacao;

import br.unisa.eventos.AbstractIntegracaoTest;
import br.unisa.eventos.ia.ResumidorAvaliacoesFake;
import br.unisa.eventos.ia.TipoInteracaoIA;
import br.unisa.eventos.ia.InteracaoIARepository;
import br.unisa.eventos.usuario.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 6 - avaliacoes e resumo por IA")
@Import(AvaliacaoIntegracaoTest.ResumidorDeTeste.class)
class AvaliacaoIntegracaoTest extends AbstractIntegracaoTest {

    @TestConfiguration
    static class ResumidorDeTeste {

        @Bean
        @Primary
        ResumidorAvaliacoesFake resumidorAvaliacoesFake() {
            return new ResumidorAvaliacoesFake();
        }
    }

    @Autowired
    private ResumidorAvaliacoesFake resumidor;

    @Autowired
    private InteracaoIARepository interacaoRepository;

    @BeforeEach
    void prepararResumidor() {
        resumidor.voltarAFuncionar();
    }

    @Test
    @DisplayName("participante com presenca avalia o evento encerrado - RN06")
    void deveAvaliarComPresencaEEventoTerminado_RN06() throws Exception {
        Autenticado organizador = autenticar("org-av@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("avaliador@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Oficina", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);

        mockMvc.perform(post("/api/inscricoes/{id}/avaliacao", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nota":5,"comentario":"Excelente oficina, aprendi muito."}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").value(5))
                .andExpect(jsonPath("$.inscricaoId").value(inscricaoId))
                .andExpect(jsonPath("$.nomeParticipante").value("avaliador"));
    }

    @Test
    @DisplayName("avaliar sem presenca registrada devolve 422 - RN06")
    void deveRecusarAvaliacaoSemPresenca_RN06() throws Exception {
        Autenticado organizador = autenticar("org-sem-pres-av@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("sem-pres-av@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Faltou", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        mockMvc.perform(post("/api/inscricoes/{id}/avaliacao", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nota":4,"comentario":"Nao fui, mas quero avaliar."}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PRESENCA_NAO_REGISTRADA"));
    }

    @Test
    @DisplayName("avaliar antes do fim do evento devolve 422 - RN06")
    void deveRecusarAvaliacaoAntesDoFimDoEvento_RN06() throws Exception {
        Autenticado organizador = autenticar("org-antes-av@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("antes-av@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEmAndamento(organizador, "Acontecendo agora", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);

        mockMvc.perform(post("/api/inscricoes/{id}/avaliacao", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nota":5,"comentario":"Esta otimo ate agora."}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EVENTO_NAO_TERMINADO"));
    }

    @Test
    @DisplayName("uma avaliacao por inscricao - RN06")
    void deveRecusarSegundaAvaliacaoDaMesmaInscricao_RN06() throws Exception {
        Autenticado organizador = autenticar("org-uma-av@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("uma-av@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Uma vez", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);
        avaliar(participante, inscricaoId, 5, "Primeira avaliacao.");

        mockMvc.perform(post("/api/inscricoes/{id}/avaliacao", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nota":1,"comentario":"Mudei de ideia."}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("AVALIACAO_JA_ENVIADA"));
    }

    @Test
    @DisplayName("nota fora de 1 a 5 devolve 422 VALIDACAO")
    void deveRecusarNotaForaDaFaixa() throws Exception {
        Autenticado organizador = autenticar("org-nota@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("nota@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Nota invalida", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);

        mockMvc.perform(post("/api/inscricoes/{id}/avaliacao", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nota":6,"comentario":"Nota alta demais."}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("nota"));
    }

    @Test
    @DisplayName("nao se avalia a inscricao de outra pessoa")
    void deveNegarAvaliacaoDeInscricaoAlheia() throws Exception {
        Autenticado organizador = autenticar("org-alheia-av@unisa.br", Papel.ORGANIZADOR);
        Autenticado dono = autenticar("dono-av@unisa.br", Papel.PARTICIPANTE);
        Autenticado intruso = autenticar("intruso-av@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Alheia", 10);
        long inscricaoId = inscreverConfirmado(dono, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);

        mockMvc.perform(post("/api/inscricoes/{id}/avaliacao", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nota":1,"comentario":"Nem fui."}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("resumo com menos de 3 avaliacoes devolve 422 - RN07")
    void deveRecusarResumoComAvaliacoesInsuficientes_RN07() throws Exception {
        Autenticado organizador = autenticar("org-poucas@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Poucas avaliacoes", 10);
        avaliarComParticipantes(organizador, eventoId, 2);

        mockMvc.perform(post("/api/eventos/{id}/avaliacoes/resumo", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("AVALIACOES_INSUFICIENTES"));
    }

    @Test
    @DisplayName("resumo e gerado a partir de 3 avaliacoes, com nota media - RN07")
    void deveGerarResumoComMinimoDeAvaliacoes_RN07() throws Exception {
        Autenticado organizador = autenticar("org-resumo@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Com resumo", 10);
        avaliarComParticipantes(organizador, eventoId, 3);

        mockMvc.perform(post("/api/eventos/{id}/avaliacoes/resumo", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(eventoId))
                .andExpect(jsonPath("$.totalAvaliacoes").value(3))
                .andExpect(jsonPath("$.notaMedia").isNotEmpty())
                .andExpect(jsonPath("$.textoResumo").isNotEmpty())
                .andExpect(jsonPath("$.geradoEm").isNotEmpty());

        assertThat(resumidor.ultimosComentarios()).hasSize(3);
    }

    @Test
    @DisplayName("regerar substitui o resumo anterior em vez de acumular - RN07")
    void deveSubstituirResumoAoRegerar_RN07() throws Exception {
        Autenticado organizador = autenticar("org-regera@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Regera", 10);
        avaliarComParticipantes(organizador, eventoId, 3);

        gerarResumo(organizador, eventoId);
        gerarResumo(organizador, eventoId);

        Long linhas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM resumo_avaliacao WHERE evento_id = ?", Long.class, eventoId);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    @DisplayName("IA indisponivel no resumo devolve 503 e registra a falha - RN07 e RN10")
    void deveDevolver503QuandoResumoFalha_RN10() throws Exception {
        Autenticado organizador = autenticar("org-resumo-falha@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Resumo falha", 10);
        avaliarComParticipantes(organizador, eventoId, 3);
        resumidor.falharNaProximaChamada();

        mockMvc.perform(post("/api/eventos/{id}/avaliacoes/resumo", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("IA_INDISPONIVEL"));

        assertThat(interacaoRepository.countByTipoAndSucesso(
                TipoInteracaoIA.RESUMO_AVALIACAO, false)).isEqualTo(1);

        Long resumos = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM resumo_avaliacao WHERE evento_id = ?", Long.class, eventoId);
        assertThat(resumos).as("falha na IA nao deixa resumo pela metade").isZero();
    }

    @Test
    @DisplayName("GET do resumo antes de gerar devolve 404")
    void deveDevolver404ParaResumoInexistente() throws Exception {
        Autenticado organizador = autenticar("org-sem-resumo@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Sem resumo", 10);

        mockMvc.perform(get("/api/eventos/{id}/avaliacoes/resumo", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("GET do resumo devolve o ultimo gerado")
    void deveDevolverUltimoResumoGerado() throws Exception {
        Autenticado organizador = autenticar("org-ultimo@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Ultimo resumo", 10);
        avaliarComParticipantes(organizador, eventoId, 3);
        gerarResumo(organizador, eventoId);

        mockMvc.perform(get("/api/eventos/{id}/avaliacoes/resumo", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAvaliacoes").value(3));
    }

    @Test
    @DisplayName("avaliacoes e resumo sao visiveis so para o organizador dono - RN08")
    void deveRestringirAvaliacoesAoDono_RN08() throws Exception {
        Autenticado dono = autenticar("org-dono-av@unisa.br", Papel.ORGANIZADOR);
        Autenticado intruso = autenticar("org-xereta-av@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoEncerradoRecentemente(dono, "Restrito", 10);
        avaliarComParticipantes(dono, eventoId, 3);

        mockMvc.perform(get("/api/eventos/{id}/avaliacoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, dono.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/eventos/{id}/avaliacoes", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/eventos/{id}/avaliacoes/resumo", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("avaliacoes sem comentario nao viram entrada do resumo")
    void deveRecusarResumoQuandoNenhumaAvaliacaoTemComentario() throws Exception {
        Autenticado organizador = autenticar("org-sem-coment@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "So notas", 10);

        for (int indice = 1; indice <= 3; indice++) {
            Autenticado participante =
                    autenticar("so-nota-%d@unisa.br".formatted(indice), Papel.PARTICIPANTE);
            long inscricaoId = inscreverConfirmado(participante, eventoId);
            registrarPresenca(organizador, eventoId, inscricaoId);
            avaliar(participante, inscricaoId, 4, null);
        }

        mockMvc.perform(post("/api/eventos/{id}/avaliacoes/resumo", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SEM_COMENTARIOS_PARA_RESUMIR"));
    }

    private void avaliarComParticipantes(Autenticado organizador, long eventoId, int quantidade)
            throws Exception {
        for (int indice = 1; indice <= quantidade; indice++) {
            Autenticado participante = autenticar(
                    "avaliador-%d-%d@unisa.br".formatted(eventoId, indice), Papel.PARTICIPANTE);
            long inscricaoId = inscreverConfirmado(participante, eventoId);
            registrarPresenca(organizador, eventoId, inscricaoId);
            avaliar(participante, inscricaoId, 3 + (indice % 3),
                    "Comentario do participante %d sobre o evento.".formatted(indice));
        }
    }

    private void avaliar(Autenticado participante, long inscricaoId, int nota, String comentario)
            throws Exception {
        String corpo = comentario == null
                ? """
                {"nota":%d}""".formatted(nota)
                : """
                {"nota":%d,"comentario":"%s"}""".formatted(nota, comentario);

        mockMvc.perform(post("/api/inscricoes/{id}/avaliacao", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
    }

    private void gerarResumo(Autenticado organizador, long eventoId) throws Exception {
        mockMvc.perform(post("/api/eventos/{id}/avaliacoes/resumo", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isOk());
    }
}
