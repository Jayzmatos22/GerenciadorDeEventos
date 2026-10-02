package br.unisa.eventos.ia;

import br.unisa.eventos.AbstractIntegracaoTest;
import br.unisa.eventos.usuario.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sem extrator falso: aqui vale o ExtratorEventoSpringAI de verdade, com o perfil de teste
 * que nao configura modelo nenhum. E o cenario de quem roda a aplicacao sem Ollama no ar.
 */
@DisplayName("Fase 5 - aplicacao sem modelo de IA configurado")
class IaNaoConfiguradaIntegracaoTest extends AbstractIntegracaoTest {

    @Test
    @DisplayName("sem modelo configurado, interpretar devolve 503 em vez de derrubar o contexto")
    void deveDevolver503SemModeloConfigurado_RN10() throws Exception {
        Autenticado organizador = autenticar("org-sem-modelo@unisa.br", Papel.ORGANIZADOR);

        mockMvc.perform(post("/api/eventos/interpretar")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"Palestra sobre Spring Boot na UNISA."}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("IA_INDISPONIVEL"));
    }
}
