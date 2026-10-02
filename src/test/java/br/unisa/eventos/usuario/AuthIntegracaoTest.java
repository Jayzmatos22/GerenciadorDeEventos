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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 1 - registro, login e JWT")
class AuthIntegracaoTest extends AbstractIntegracaoTest {

    @Test
    @DisplayName("registra participante por padrao quando o papel nao e informado")
    void deveRegistrarComoParticipantePorPadrao() throws Exception {
        mockMvc.perform(post("/api/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Jailton","email":"jailton@unisa.br","senha":"senha-forte-1"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("jailton@unisa.br"))
                .andExpect(jsonPath("$.papeis[0]").value("PARTICIPANTE"));
    }

    @Test
    @DisplayName("senha com menos de 8 caracteres e recusada com 422 VALIDACAO_RN11")
    void deveRecusarSenhaCurta_RN11() throws Exception {
        mockMvc.perform(post("/api/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Jailton","email":"curta@unisa.br","senha":"1234567"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("senha"))
                .andExpect(jsonPath("$.caminho").value("/api/auth/registrar"));
    }

    @Test
    @DisplayName("a senha e persistida como hash BCrypt, nunca em claro - RN11")
    void devePersistirSenhaComoHashBCrypt_RN11() {
        autenticar("hash@unisa.br", Papel.PARTICIPANTE);

        String hash = jdbcTemplate.queryForObject(
                "SELECT senha_hash FROM usuario WHERE email = 'hash@unisa.br'", String.class);

        assertThat(hash).startsWith("$2a$10$").isNotEqualTo("senha-de-teste");
    }

    @Test
    @DisplayName("e-mail repetido devolve 422 EMAIL_EM_USO")
    void deveRecusarEmailDuplicado() throws Exception {
        autenticar("duplicado@unisa.br", Papel.PARTICIPANTE);

        mockMvc.perform(post("/api/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Outro","email":"duplicado@unisa.br","senha":"senha-forte-1"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EMAIL_EM_USO"));
    }

    @Test
    @DisplayName("login devolve token, validade e usuario")
    void deveDevolverTokenNoLogin() throws Exception {
        autenticar("login@unisa.br", Papel.ORGANIZADOR);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@unisa.br","senha":"senha-de-teste"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andExpect(jsonPath("$.usuario.papeis[0]").value("ORGANIZADOR"));
    }

    @Test
    @DisplayName("senha errada devolve 401 CREDENCIAIS_INVALIDAS")
    void deveRecusarSenhaErrada() throws Exception {
        autenticar("senha-errada@unisa.br", Papel.PARTICIPANTE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"senha-errada@unisa.br","senha":"senha-incorreta"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    @DisplayName("e-mail inexistente devolve 401, sem revelar que o usuario nao existe")
    void deveRecusarEmailInexistente() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ninguem@unisa.br","senha":"senha-de-teste"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha invalidos."));
    }

    @Test
    @DisplayName("/auth/eu devolve o usuario do token")
    void deveDevolverUsuarioLogado() throws Exception {
        Autenticado usuario = autenticar("eu@unisa.br", Papel.ORGANIZADOR);

        mockMvc.perform(get("/api/auth/eu").header(HttpHeaders.AUTHORIZATION, usuario.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.id()))
                .andExpect(jsonPath("$.email").value("eu@unisa.br"));
    }

    @Test
    @DisplayName("/auth/eu sem token devolve 401 no envelope padrao")
    void deveRecusarRotaAutenticadaSemToken() throws Exception {
        mockMvc.perform(get("/api/auth/eu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.caminho").value("/api/auth/eu"));
    }

    @Test
    @DisplayName("token com payload adulterado devolve 401 e nao autentica")
    void deveRecusarTokenAdulterado() throws Exception {
        Autenticado usuario = autenticar("adulterado@unisa.br", Papel.PARTICIPANTE);

        // Troca o payload por um que afirma ser ORGANIZADOR do usuario 999. A assinatura
        // continua sendo a do token original, logo a verificacao HS256 precisa recusar.
        String[] partes = usuario.token().split("\\.");
        String payloadForjado = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                {"sub":"999","nome":"Invasor","email":"invasor@unisa.br","papeis":["ORGANIZADOR"]}"""
                .getBytes(StandardCharsets.UTF_8));
        String forjado = partes[0] + "." + payloadForjado + "." + partes[2];

        mockMvc.perform(get("/api/auth/eu")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forjado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    @DisplayName("token assinado com outro segredo devolve 401")
    void deveRecusarTokenComAssinaturaDeOutroSegredo() throws Exception {
        Autenticado usuario = autenticar("outro-segredo@unisa.br", Papel.PARTICIPANTE);

        String[] partes = usuario.token().split("\\.");
        String assinaturaFalsa = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[32]);
        String forjado = partes[0] + "." + partes[1] + "." + assinaturaFalsa;

        mockMvc.perform(get("/api/auth/eu")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forjado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    @DisplayName("nenhuma resposta de erro expoe stacktrace")
    void naoDeveExporStacktrace() throws Exception {
        String corpo = mockMvc.perform(get("/api/auth/eu"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("Exception").doesNotContain("br.unisa.eventos");
    }
}
