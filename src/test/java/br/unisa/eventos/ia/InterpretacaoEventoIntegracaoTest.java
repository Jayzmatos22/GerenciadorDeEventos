package br.unisa.eventos.ia;

import br.unisa.eventos.AbstractIntegracaoTest;
import br.unisa.eventos.ia.dto.EventoExtraidoDTO;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 5 - extracao de evento por IA")
@Import(InterpretacaoEventoIntegracaoTest.ExtratorDeTeste.class)
class InterpretacaoEventoIntegracaoTest extends AbstractIntegracaoTest {

    @TestConfiguration
    static class ExtratorDeTeste {

        @Bean
        @Primary
        ExtratorEventoFake extratorEventoFake() {
            return new ExtratorEventoFake();
        }
    }

    @Autowired
    private ExtratorEventoFake extrator;

    @Autowired
    private InteracaoIARepository interacaoRepository;

    @BeforeEach
    void prepararExtrator() {
        extrator.voltarAFuncionar();
    }

    @Test
    @DisplayName("devolve o rascunho extraido e nao persiste evento - RF03")
    void deveDevolverRascunhoSemPersistirEvento_RF03() throws Exception {
        Autenticado organizador = autenticar("org-ia@unisa.br", Papel.ORGANIZADOR);

        mockMvc.perform(post("/api/eventos/interpretar")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"Semana de Tecnologia no auditorio da UNISA, dia 10 de
                                 novembro de 2026 as 19h, 3 horas, 120 vagas."}"""
                                .replace("\n", " ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Semana de Tecnologia"))
                .andExpect(jsonPath("$.local").value("Auditorio UNISA"))
                .andExpect(jsonPath("$.cargaHoraria").value(3))
                .andExpect(jsonPath("$.limiteVagas").value(120));

        Long eventosCriados = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento", Long.class);
        assertThat(eventosCriados).as("interpretar nao persiste evento").isZero();
    }

    @Test
    @DisplayName("campos ausentes no texto voltam nulos e listados em camposNaoIdentificados")
    void deveListarCamposNaoIdentificados() throws Exception {
        Autenticado organizador = autenticar("org-parcial@unisa.br", Papel.ORGANIZADOR);
        extrator.responderCom(new EventoExtraidoDTO("Roda de conversa", null, null,
                null, null, null, null, List.of("descricao", "local", "dataInicio",
                "dataFim", "cargaHoraria", "limiteVagas")));

        mockMvc.perform(post("/api/eventos/interpretar")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"Vamos fazer uma roda de conversa."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Roda de conversa"))
                .andExpect(jsonPath("$.local").doesNotExist())
                .andExpect(jsonPath("$.camposNaoIdentificados.length()").value(6));
    }

    @Test
    @DisplayName("extrator falhando devolve 503 IA_INDISPONIVEL e o cadastro manual segue - RN10")
    void deveDevolver503EManterFluxoManualQuandoIaFalha_RN10() throws Exception {
        Autenticado organizador = autenticar("org-falha-ia@unisa.br", Papel.ORGANIZADOR);
        extrator.falharNaProximaChamada();

        mockMvc.perform(post("/api/eventos/interpretar")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"Qualquer texto de evento."}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("IA_INDISPONIVEL"))
                .andExpect(jsonPath("$.caminho").value("/api/eventos/interpretar"));

        // O formulario manual continua funcionando: e isso que a RN-10 protege.
        long eventoId = criarEvento(organizador, "Cadastrado na mao", 20,
                java.time.LocalDateTime.now().plusDays(5),
                java.time.LocalDateTime.now().plusDays(5).plusHours(3));
        assertThat(eventoId).isPositive();
    }

    @Test
    @DisplayName("a resposta de falha da IA nao expoe stacktrace - RN10")
    void naoDeveExporStacktraceNaFalhaDaIa_RN10() throws Exception {
        Autenticado organizador = autenticar("org-stack@unisa.br", Papel.ORGANIZADOR);
        extrator.falharNaProximaChamada();

        String corpo = mockMvc.perform(post("/api/eventos/interpretar")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"Evento qualquer."}"""))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("Exception").doesNotContain("br.unisa.eventos");
    }

    @Test
    @DisplayName("toda chamada ao modelo e registrada em interacao_ia, com e sem sucesso")
    void deveRegistrarTodaChamadaEmInteracaoIa() throws Exception {
        Autenticado organizador = autenticar("org-auditoria@unisa.br", Papel.ORGANIZADOR);

        interpretar(organizador, "Texto que da certo.", status().isOk());
        extrator.falharNaProximaChamada();
        interpretar(organizador, "Texto que falha.", status().isServiceUnavailable());

        assertThat(interacaoRepository.countByTipoAndSucesso(
                TipoInteracaoIA.EXTRACAO_EVENTO, true)).isEqualTo(1);
        assertThat(interacaoRepository.countByTipoAndSucesso(
                TipoInteracaoIA.EXTRACAO_EVENTO, false))
                .as("a falha tambem fica registrada, mesmo com a requisicao terminando em 503")
                .isEqualTo(1);

        String entradaDaFalha = jdbcTemplate.queryForObject(
                "SELECT texto_entrada FROM interacao_ia WHERE sucesso = false", String.class);
        assertThat(entradaDaFalha).isEqualTo("Texto que falha.");
    }

    @Test
    @DisplayName("participante nao usa a interpretacao por IA")
    void deveNegarInterpretacaoParaParticipante() throws Exception {
        Autenticado participante = autenticar("part-ia@unisa.br", Papel.PARTICIPANTE);

        mockMvc.perform(post("/api/eventos/interpretar")
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"Tentativa."}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("texto em branco devolve 422 VALIDACAO")
    void deveRecusarTextoEmBranco() throws Exception {
        Autenticado organizador = autenticar("org-vazio@unisa.br", Papel.ORGANIZADOR);

        mockMvc.perform(post("/api/eventos/interpretar")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"   "}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("texto"));
    }

    private void interpretar(Autenticado organizador, String texto,
                             org.springframework.test.web.servlet.ResultMatcher esperado)
            throws Exception {
        mockMvc.perform(post("/api/eventos/interpretar")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TextoDeEntrada(texto))))
                .andExpect(esperado);
    }

    private record TextoDeEntrada(String texto) {
    }
}
