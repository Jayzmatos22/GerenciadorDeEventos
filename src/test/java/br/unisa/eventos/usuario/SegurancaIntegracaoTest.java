package br.unisa.eventos.usuario;

import br.unisa.eventos.AbstractIntegracaoTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achados da revisão de segurança, travados como regressão.
 */
@DisplayName("Seguranca - regressoes")
class SegurancaIntegracaoTest extends AbstractIntegracaoTest {

    @Test
    @DisplayName("senha acima de 72 bytes vira 422 de validacao, nao 500")
    void deveRecusarSenhaAcimaDoLimiteDoBCrypt() throws Exception {
        // 72 caracteres acentuados ocupam 144 bytes em UTF-8: o encoder do Spring Security
        // recusa, e antes disso virava erro interno.
        String senha = "á".repeat(72);

        mockMvc.perform(post("/api/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Teste","email":"longa@unisa.br","senha":"%s"}"""
                                .formatted(senha)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("senha"));
    }

    @Test
    @DisplayName("senha longa dentro do limite de bytes continua aceita")
    void deveAceitarSenhaLongaDentroDoLimite() throws Exception {
        String senha = "a".repeat(72);

        mockMvc.perform(post("/api/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Teste","email":"no-limite@unisa.br","senha":"%s"}"""
                                .formatted(senha)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("login compara a senha mesmo com e-mail inexistente, para nao vazar quem tem conta")
    void naoDeveVazarExistenciaDeContaPeloTempoDeResposta() throws Exception {
        autenticar("existe@unisa.br", Papel.PARTICIPANTE);

        long comConta = tempoDeLogin("existe@unisa.br");
        long semConta = tempoDeLogin("ninguem@unisa.br");

        // O BCrypt domina o tempo dos dois caminhos. A folga é larga de propósito: o teste
        // existe para pegar o retorno antecipado, não para medir desempenho.
        double razao = (double) Math.max(comConta, semConta) / Math.max(1, Math.min(comConta, semConta));
        assertThat(razao)
                .as("tempo de login com conta (%d ms) e sem conta (%d ms) na mesma ordem de grandeza",
                        comConta, semConta)
                .isLessThan(5.0);
    }

    @Test
    @DisplayName("token sem assinatura (alg none) e recusado")
    void deveRecusarTokenComAlgoritmoNone() throws Exception {
        String cabecalho = base64Url("""
                {"alg":"none","typ":"JWT"}""");
        String corpo = base64Url("""
                {"sub":"1","nome":"Invasor","email":"invasor@unisa.br","papeis":["ORGANIZADOR"]}""");

        mockMvc.perform(get("/api/auth/eu")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer %s.%s.".formatted(cabecalho, corpo)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    @DisplayName("respostas trazem os cabecalhos de seguranca do Spring Security")
    void deveEnviarCabecalhosDeSeguranca() throws Exception {
        mockMvc.perform(get("/api/eventos"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("a documentacao da API fica desligada fora do perfil de desenvolvimento")
    void naoDeveExporDocumentacaoForaDoPerfilDev() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    private long tempoDeLogin(String email) throws Exception {
        // Uma chamada antes de medir, para o JIT e o pool de conexões não entrarem na conta.
        executarLogin(email);

        long inicio = System.nanoTime();
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            executarLogin(email);
        }
        return (System.nanoTime() - inicio) / 5 / 1_000_000;
    }

    private void executarLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha-que-nao-confere"}""".formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    private String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
