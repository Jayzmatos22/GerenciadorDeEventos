package br.unisa.eventos.certificado;

import br.unisa.eventos.AbstractIntegracaoTest;
import br.unisa.eventos.usuario.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 4 - certificado em PDF e validacao publica")
class CertificadoIntegracaoTest extends AbstractIntegracaoTest {

    @Test
    @DisplayName("certificado e negado sem presenca registrada - RN05")
    void deveNegarCertificadoSemPresenca_RN05() throws Exception {
        Autenticado organizador = autenticar("org-sem-presenca@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("sem-presenca@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEmAndamento(organizador, "Sem check-in", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);

        mockMvc.perform(post("/api/inscricoes/{id}/certificado", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PRESENCA_NAO_REGISTRADA"));
    }

    @Test
    @DisplayName("emite o certificado com presenca e codigo em UUID sem hifens - RN05")
    void deveEmitirCertificadoComPresenca_RN05() throws Exception {
        Autenticado organizador = autenticar("org-cert@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("certificado@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Com certificado", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);

        String resposta = mockMvc.perform(post("/api/inscricoes/{id}/certificado", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inscricaoId").value(inscricaoId))
                .andReturn().getResponse().getContentAsString();

        String codigo = objectMapper.readTree(resposta).get("codigoAutenticidade").asText();
        assertThat(codigo).hasSize(32).matches("[0-9A-F]{32}");
        assertThat(objectMapper.readTree(resposta).get("urlValidacao").asText())
                .isEqualTo("http://localhost:8080/certificados/validar/" + codigo);
    }

    @Test
    @DisplayName("emitir duas vezes devolve o mesmo certificado - RN05")
    void deveSerIdempotenteNaEmissao_RN05() throws Exception {
        Autenticado organizador = autenticar("org-idem-cert@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("idem-cert@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Idempotente", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);

        String primeira = emitir(participante, inscricaoId);
        String segunda = emitir(participante, inscricaoId);

        assertThat(objectMapper.readTree(segunda).get("codigoAutenticidade").asText())
                .isEqualTo(objectMapper.readTree(primeira).get("codigoAutenticidade").asText());
        assertThat(objectMapper.readTree(segunda).get("id").asLong())
                .isEqualTo(objectMapper.readTree(primeira).get("id").asLong());

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM certificado WHERE inscricao_id = ?", Long.class, inscricaoId);
        assertThat(total).isEqualTo(1);
    }

    @Test
    @DisplayName("o download devolve um PDF de verdade")
    void deveBaixarPdfDoCertificado() throws Exception {
        Autenticado organizador = autenticar("org-pdf@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("pdf@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Oficina de Docker", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);
        emitir(participante, inscricaoId);

        byte[] pdf = mockMvc.perform(get("/api/inscricoes/{id}/certificado", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf).hasSizeGreaterThan(1000);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("certificado de outra pessoa devolve 403")
    void deveNegarCertificadoDeTerceiro() throws Exception {
        Autenticado organizador = autenticar("org-terceiro@unisa.br", Papel.ORGANIZADOR);
        Autenticado dono = autenticar("dono-cert@unisa.br", Papel.PARTICIPANTE);
        Autenticado intruso = autenticar("intruso-cert@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Protegido", 10);
        long inscricaoId = inscreverConfirmado(dono, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);
        emitir(dono, inscricaoId);

        mockMvc.perform(get("/api/inscricoes/{id}/certificado", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("validacao publica devolve nome, evento, carga horaria e datas")
    void deveValidarCertificadoPublicamente() throws Exception {
        Autenticado organizador = autenticar("org-valida@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("valida@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Semana de ADS", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);
        String codigo = objectMapper.readTree(emitir(participante, inscricaoId))
                .get("codigoAutenticidade").asText();

        // Sem header Authorization: a rota de validacao e publica.
        mockMvc.perform(get("/api/certificados/validar/{codigo}", codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoAutenticidade").value(codigo))
                .andExpect(jsonPath("$.nomeParticipante").value("valida"))
                .andExpect(jsonPath("$.tituloEvento").value("Semana de ADS"))
                .andExpect(jsonPath("$.cargaHoraria").value(4))
                .andExpect(jsonPath("$.dataEmissao").isNotEmpty());
    }

    @Test
    @DisplayName("codigo inexistente na validacao devolve 404")
    void deveDevolver404ParaCodigoInexistente() throws Exception {
        mockMvc.perform(get("/api/certificados/validar/{codigo}", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("o PDF nao fica em disco na v1: arquivo_url permanece nulo")
    void naoDevePersistirArquivoDoCertificado() throws Exception {
        Autenticado organizador = autenticar("org-arquivo@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("arquivo-cert@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Sob demanda", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);
        emitir(participante, inscricaoId);

        String arquivoUrl = jdbcTemplate.queryForObject(
                "SELECT arquivo_url FROM certificado WHERE inscricao_id = ?",
                String.class, inscricaoId);
        assertThat(arquivoUrl).isNull();
    }

    @Test
    @DisplayName("baixar antes de emitir devolve 404")
    void deveDevolver404AoBaixarSemEmitir() throws Exception {
        Autenticado organizador = autenticar("org-nao-emitido@unisa.br", Papel.ORGANIZADOR);
        Autenticado participante = autenticar("nao-emitido@unisa.br", Papel.PARTICIPANTE);
        long eventoId = criarEventoEncerradoRecentemente(organizador, "Nao emitido", 10);
        long inscricaoId = inscreverConfirmado(participante, eventoId);
        registrarPresenca(organizador, eventoId, inscricaoId);

        mockMvc.perform(get("/api/inscricoes/{id}/certificado", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    private String emitir(Autenticado participante, long inscricaoId) throws Exception {
        return mockMvc.perform(post("/api/inscricoes/{id}/certificado", inscricaoId)
                        .header(HttpHeaders.AUTHORIZATION, participante.header()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}
