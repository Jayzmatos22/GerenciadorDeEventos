package br.unisa.eventos.evento;

import br.unisa.eventos.AbstractIntegracaoTest;
import br.unisa.eventos.usuario.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 2 - eventos, fotos e transicoes de status")
class EventoIntegracaoTest extends AbstractIntegracaoTest {

    @Test
    @DisplayName("organizador cria evento em RASCUNHO")
    void deveCriarEventoEmRascunho() throws Exception {
        Autenticado organizador = autenticar("org@unisa.br", Papel.ORGANIZADOR);

        mockMvc.perform(post("/api/eventos")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Semana de Tecnologia","descricao":"Palestras e oficinas",
                                 "local":"Auditorio UNISA","dataInicio":"2026-11-10T19:00:00",
                                 "dataFim":"2026-11-10T22:00:00","cargaHoraria":3,
                                 "limiteVagas":50}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.vagasRestantes").value(50))
                .andExpect(jsonPath("$.organizador.id").value(organizador.id()));
    }

    @Test
    @DisplayName("participante nao cria evento: 403 ACESSO_NEGADO")
    void deveNegarCriacaoParaParticipante() throws Exception {
        Autenticado participante = autenticar("part@unisa.br", Papel.PARTICIPANTE);

        mockMvc.perform(post("/api/eventos")
                        .header(HttpHeaders.AUTHORIZATION, participante.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Tentativa","descricao":"x","local":"y",
                                 "dataInicio":"2026-11-10T19:00:00","dataFim":"2026-11-10T22:00:00",
                                 "cargaHoraria":3,"limiteVagas":10}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("data de fim anterior a de inicio devolve 422 PERIODO_INVALIDO")
    void deveRecusarPeriodoInvertido() throws Exception {
        Autenticado organizador = autenticar("periodo@unisa.br", Papel.ORGANIZADOR);

        mockMvc.perform(post("/api/eventos")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Invertido","descricao":"x","local":"y",
                                 "dataInicio":"2026-11-10T22:00:00","dataFim":"2026-11-10T19:00:00",
                                 "cargaHoraria":3,"limiteVagas":10}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    @Test
    @DisplayName("limite de vagas zero devolve 422 VALIDACAO")
    void deveRecusarLimiteDeVagasZero() throws Exception {
        Autenticado organizador = autenticar("vagas@unisa.br", Papel.ORGANIZADOR);

        mockMvc.perform(post("/api/eventos")
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Sem vagas","descricao":"x","local":"y",
                                 "dataInicio":"2026-11-10T19:00:00","dataFim":"2026-11-10T22:00:00",
                                 "cargaHoraria":3,"limiteVagas":0}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("limiteVagas"));
    }

    @Test
    @DisplayName("outro organizador nao edita evento alheio - RN08")
    void deveNegarEdicaoPorOrganizadorQueNaoEDono_RN08() throws Exception {
        Autenticado dono = autenticar("dono@unisa.br", Papel.ORGANIZADOR);
        Autenticado intruso = autenticar("intruso@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEvento(dono, "Workshop", 30,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(4));

        mockMvc.perform(put("/api/eventos/{id}", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, intruso.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Sequestrado","descricao":"x","local":"y",
                                 "dataInicio":"2026-11-10T19:00:00","dataFim":"2026-11-10T22:00:00",
                                 "cargaHoraria":3,"limiteVagas":10}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("evento CANCELADO nao aceita edicao - RN09")
    void deveNegarEdicaoDeEventoTerminal_RN09() throws Exception {
        Autenticado organizador = autenticar("terminal@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEvento(organizador, "Cancelado", 10,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2));
        mudarStatus(organizador, eventoId, "CANCELADO");

        mockMvc.perform(put("/api/eventos/{id}", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Editado","descricao":"x","local":"y",
                                 "dataInicio":"2026-11-10T19:00:00","dataFim":"2026-11-10T22:00:00",
                                 "cargaHoraria":3,"limiteVagas":10}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EVENTO_NAO_EDITAVEL"));
    }

    @Test
    @DisplayName("transicao de ENCERRADO para PUBLICADO e recusada - RN09")
    void deveRecusarTransicaoInvalida_RN09() throws Exception {
        Autenticado organizador = autenticar("transicao@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoPublicado(organizador, "Encerrado", 10);
        mudarStatus(organizador, eventoId, "ENCERRADO");

        mockMvc.perform(patch("/api/eventos/{id}/status", eventoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PUBLICADO"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICAO_INVALIDA"));
    }

    @Test
    @DisplayName("listagem publica mostra apenas eventos PUBLICADOS")
    void deveListarApenasPublicados() throws Exception {
        Autenticado organizador = autenticar("lista@unisa.br", Papel.ORGANIZADOR);
        criarEventoPublicado(organizador, "Visivel", 10);
        criarEvento(organizador, "Rascunho oculto", 10,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(2));

        mockMvc.perform(get("/api/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Visivel"));
    }

    @Test
    @DisplayName("filtro q busca por titulo, descricao e local - RF04")
    void deveFiltrarPorTermo() throws Exception {
        Autenticado organizador = autenticar("filtro@unisa.br", Papel.ORGANIZADOR);
        criarEventoPublicado(organizador, "Oficina de Docker", 10);
        criarEventoPublicado(organizador, "Palestra de Carreira", 10);

        mockMvc.perform(get("/api/eventos").param("q", "docker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Oficina de Docker"));
    }

    @Test
    @DisplayName("filtro por janela de datas respeita o intervalo")
    void deveFiltrarPorJanelaDeDatas() throws Exception {
        Autenticado organizador = autenticar("datas@unisa.br", Papel.ORGANIZADOR);
        long proximo = criarEvento(organizador, "Proximo", 10,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2));
        long distante = criarEvento(organizador, "Distante", 10,
                LocalDateTime.now().plusDays(60), LocalDateTime.now().plusDays(60).plusHours(2));
        publicar(organizador, proximo);
        publicar(organizador, distante);

        mockMvc.perform(get("/api/eventos")
                        .param("dataFim", LocalDateTime.now().plusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Proximo"));
    }

    @Test
    @DisplayName("detalhe do evento e publico e lista fotos")
    void deveExporDetalhePublicoComFotos() throws Exception {
        Autenticado organizador = autenticar("detalhe@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEventoPublicado(organizador, "Com foto", 10);

        var foto = new MockMultipartFile("arquivo", "banner.png", "image/png",
                "conteudo-ficticio".getBytes());
        mockMvc.perform(multipart("/api/eventos/{id}/fotos", eventoId).file(foto)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith(
                        "/uploads/eventos/" + eventoId + "/")));

        mockMvc.perform(get("/api/eventos/{id}", eventoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos.length()").value(1));
    }

    @Test
    @DisplayName("formato de imagem nao aceito devolve 422 ARQUIVO_INVALIDO")
    void deveRecusarFormatoDeArquivoNaoAceito() throws Exception {
        Autenticado organizador = autenticar("arquivo@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEvento(organizador, "Sem foto", 10,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

        var arquivo = new MockMultipartFile("arquivo", "virus.exe",
                "application/octet-stream", "mal".getBytes());

        mockMvc.perform(multipart("/api/eventos/{id}/fotos", eventoId).file(arquivo)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ARQUIVO_INVALIDO"));
    }

    @Test
    @DisplayName("remover foto devolve 204 e apaga o registro")
    void deveRemoverFoto() throws Exception {
        Autenticado organizador = autenticar("remove@unisa.br", Papel.ORGANIZADOR);
        long eventoId = criarEvento(organizador, "Remover foto", 10,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

        var foto = new MockMultipartFile("arquivo", "a.jpg", "image/jpeg", "x".getBytes());
        String criada = mockMvc.perform(multipart("/api/eventos/{id}/fotos", eventoId).file(foto)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fotoId = objectMapper.readTree(criada).get("id").asLong();

        mockMvc.perform(delete("/api/eventos/{id}/fotos/{fotoId}", eventoId, fotoId)
                        .header(HttpHeaders.AUTHORIZATION, organizador.header()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/eventos/{id}", eventoId))
                .andExpect(jsonPath("$.fotos.length()").value(0));
    }

    @Test
    @DisplayName("evento inexistente devolve 404 RECURSO_NAO_ENCONTRADO")
    void deveDevolver404ParaEventoInexistente() throws Exception {
        mockMvc.perform(get("/api/eventos/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("/eventos/meus exige autenticacao mesmo sendo um sub-caminho de /eventos/{id}")
    void deveExigirAutenticacaoEmEventosMeus() throws Exception {
        mockMvc.perform(get("/api/eventos/meus"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }
}
