"""Diagramas do Apendice A, conferidos contra o codigo.

Regras que guiaram o conteudo:
- Nenhuma operacao aparece num diagrama de classes sem existir no codigo. As do diagrama
  anterior (publicar, encerrar, vagasDisponiveis, estaLotado, confirmar, enfileirar...) nao
  existem e foram trocadas pelas reais (mudarStatus, promover, reposicionarFila...).
- A inscricao nao transita para AUSENTE: o valor existe no enum, mas nenhum fluxo da v1 o
  grava (ver StatusInscricao). Por isso a seta saiu e a nota explica.
- O PDF do certificado nao e gerado na emissao, e sim no download, em outra requisicao
  (CertificadoService.gerarPdf). Por isso a sequencia do certificado virou duas.
"""
from motor import Diagrama, Linha, Rotulo, largura_texto

L = Linha


# =========================================================================== casos de uso

def casos_participante() -> Diagrama:
    d = Diagrama("A1a", "A.1a Casos de uso — Visitante e Participante", 1230, 800)
    d.no("fronteira", "sis", 230, 40, 960, 720, ["Gerenciador de Eventos Acadêmicos"], z=0)

    def caso(id, x, y, nome, ref, w=260):
        return d.no("caso", id, x, y, w, 62, [nome, (ref, "m")])

    caso("conta", 330, 90, "Criar conta e entrar", "RF-01")
    caso("busca", 330, 180, "Pesquisar e filtrar eventos", "RF-04")
    caso("valida", 330, 270, "Validar certificado", "validação pública")
    caso("inscr", 330, 400, "Inscrever-se em evento", "RF-05")
    caso("cancel", 330, 490, "Cancelar inscrição", "RF-07")
    caso("emite", 330, 580, "Emitir certificado", "RF-09")
    caso("avalia", 330, 670, "Avaliar evento", "RF-10")
    caso("aloca", 820, 400, "Alocar vaga ou fila de espera", "RF-06 · RN-01", w=290)
    caso("promove", 820, 490, "Promover o primeiro da fila", "RF-06 · RN-02", w=290)
    caso("presenca", 820, 580, "Verificar presença", "RN-05", w=290)

    d.no("ator", "visit", 100, 150, 40, 60, ["Visitante"])
    d.no("ator", "part", 100, 520, 40, 60, ["Participante"])

    for alvo in ("conta", "busca", "valida"):
        d.aresta("assoc", "visit", alvo, (1, 0.4), (0, 0.5))
    for alvo in ("inscr", "cancel", "emite", "avalia"):
        d.aresta("assoc", "part", alvo, (1, 0.4), (0, 0.5))
    d.aresta("heranca", "part", "visit", (0.5, 0), (0.5, 1), dy_entrada=30)
    d.aresta("include", "inscr", "aloca", (1, 0.5), (0, 0.5), rotulos=[("«include»", 0, 0, -11)])
    d.aresta("extend", "promove", "cancel", (0, 0.5), (1, 0.5), rotulos=[("«extend»", 0, 0, -11)])
    d.aresta("include", "emite", "presenca", (1, 0.5), (0, 0.5),
             rotulos=[("«include»", 0, 0, -11)])
    return d


def casos_organizador() -> Diagrama:
    d = Diagrama("A1b", "A.1b Casos de uso — Organizador", 1340, 800)
    d.no("fronteira", "sis", 230, 40, 880, 720, ["Gerenciador de Eventos Acadêmicos"], z=0)

    def caso(id, x, y, nome, ref, w=290):
        return d.no("caso", id, x, y, w, 62, [nome, (ref, "m")])

    caso("cria", 310, 90, "Criar evento", "RF-02")
    caso("status", 310, 190, "Gerenciar situação do evento", "publicar · iniciar · encerrar · cancelar")
    caso("fotos", 310, 290, "Enviar fotos de divulgação", "RF-02")
    caso("inscritos", 310, 390, "Consultar inscritos e fila", "RN-08")
    caso("presenca", 310, 490, "Registrar presença", "RF-08 · RN-04")
    caso("resumo", 310, 590, "Gerar resumo das avaliações", "RF-10 · RN-07")
    caso("ia", 760, 90, "Preencher campos por IA", "RF-03 · RN-10", w=300)

    d.no("ator", "org", 100, 330, 40, 60, ["Organizador"])
    d.no("ator", "llm", 1200, 300, 40, 60, ["Serviço de IA", ("Groq", "m")])

    for alvo in ("cria", "status", "fotos", "inscritos", "presenca", "resumo"):
        d.aresta("assoc", "org", alvo, (1, 0.4), (0, 0.5))
    d.aresta("extend", "ia", "cria", (0, 0.5), (1, 0.5), rotulos=[("«extend»", 0, 0, -11)])
    d.aresta("assoc", "llm", "ia", (0, 0.4), (1, 0.5))
    d.aresta("assoc", "llm", "resumo", (0, 0.4), (1, 0.5))
    d.no("nota", "n", 310, 688, 560, 46,
         ["Toda ação sobre um evento exige que o usuário seja o organizador",
          "que o criou — verificado no service, não só pelo papel (RN-08)."])
    return d


# =========================================================================== classes

def classes_nucleo() -> Diagrama:
    d = Diagrama("A2a", "A.2a Classes — Usuários, eventos e inscrições", 1320, 740)
    d.classe("usuario", 60, 70, 300, "Usuario",
             ["- nome: String", "- email: String", "- senhaHash: String",
              "- criadoEm: LocalDateTime"],
             ["+ adicionarPapel(papel: Papel)", "+ temPapel(papel: Papel): boolean"])
    d.classe("upapel", 100, 330, 220, "UsuarioPapel", ["- papel: Papel"])
    d.classe("papel", 100, 470, 220, "Papel", ["PARTICIPANTE", "ORGANIZADOR"],
             estereotipo="enumeration")
    d.classe("evento", 500, 70, 340, "Evento",
             ["- titulo: String", "- descricao: String", "- local: String",
              "- dataInicio: LocalDateTime", "- dataFim: LocalDateTime",
              "- cargaHoraria: int", "- limiteVagas: int", "- status: StatusEvento",
              "- versao: long"],
             ["+ atualizarDados(…)", "+ mudarStatus(novo: StatusEvento)",
              "+ adicionarFoto(url: String): EventoFoto",
              "+ removerFoto(fotoId: Long): boolean",
              "+ pertenceA(usuarioId: Long): boolean"])
    d.classe("foto", 980, 90, 240, "EventoFoto", ["- url: String", "- ordem: int"])
    d.classe("sevento", 980, 250, 300, "StatusEvento",
             ["RASCUNHO", "PUBLICADO", "EM_ANDAMENTO", "ENCERRADO", "CANCELADO"],
             ["+ podeTransitarPara(destino): boolean", "+ aceitaInscricao(): boolean",
              "+ terminal(): boolean"], estereotipo="enumeration")
    d.classe("inscricao", 500, 470, 340, "Inscricao",
             ["- status: StatusInscricao", "- posicaoFila: Integer",
              "- dataInscricao: LocalDateTime"],
             [("+ confirmada(usuario, evento): Inscricao", "u"),
              ("+ emEspera(usuario, evento, posicao): Inscricao", "u"),
              "+ promover()", "+ cancelar()", "+ reposicionarFila(novaPosicao: int)",
              "+ pertenceA(usuarioId: Long): boolean"])
    d.classe("sinscricao", 980, 528, 300, "StatusInscricao",
             ["CONFIRMADA", "EM_ESPERA", "CANCELADA", "AUSENTE"],
             ["+ ativa(): boolean"], estereotipo="enumeration")

    u, ev, ins = d.nos["usuario"], d.nos["evento"], d.nos["inscricao"]
    d.aresta("assoc", "usuario", "evento", (1, (110 - u.y) / u.h), (0, (110 - ev.y) / ev.h),
             rotulos=[("organiza", 0, 0, -11), ("1", -0.8, 0, -11), ("0..*", 0.75, 0, -11)])
    d.aresta("composicao", "usuario", "upapel", (0.5, 1), (0.5, 0),
             rotulos=[("1..*", 0.6, 22, 0)])
    d.aresta("assoc", "usuario", "inscricao", (1, (205 - u.y) / u.h),
             (0, (560 - ins.y) / ins.h), pontos=[(430, 205), (430, 560)],
             rotulos=[("inscreve-se", 0, 0, 0), ("1", -0.88, 0, -11),
                      ("0..*", 0.9, 0, -11)])
    d.aresta("assoc", "evento", "inscricao", (0.5, 1), (0.5, 0),
             rotulos=[("1", -0.6, 14, 0), ("0..*", 0.55, 22, 0)])
    d.aresta("composicao", "evento", "foto", (1, (128 - ev.y) / ev.h), (0, 0.5),
             rotulos=[("0..*", 0.65, 0, -11)])
    return d


def classes_participacao() -> Diagrama:
    d = Diagrama("A2b", "A.2b Classes — Participação e IA", 1300, 760)
    d.classe("presenca", 60, 70, 300, "Presenca",
             ["- dataHoraCheckin: LocalDateTime", "- registradoPor: Usuario"])
    d.classe("certificado", 500, 70, 300, "Certificado",
             ["- codigoAutenticidade: String", "- dataEmissao: LocalDateTime",
              "- arquivoUrl: String"],
             [("- gerarCodigo(): String", "u")])
    d.classe("avaliacao", 940, 70, 300, "Avaliacao",
             ["- nota: int", "- comentario: String", "- dataEnvio: LocalDateTime"])
    d.no("referencia", "inscricao", 500, 300, 300, 46, [("Inscricao", "b"), ("ver A.2a", "m")])
    d.no("referencia", "evento", 60, 450, 300, 46, [("Evento", "b"), ("ver A.2a", "m")])
    d.no("referencia", "usuario", 940, 450, 300, 46, [("Usuario", "b"), ("ver A.2a", "m")])
    d.classe("resumo", 60, 570, 300, "ResumoAvaliacao",
             ["- textoResumo: String", "- notaMedia: BigDecimal",
              "- totalAvaliacoes: Integer", "- geradoEm: LocalDateTime"],
             ["+ atualizar(texto, media, total)"])
    d.classe("interacao", 940, 570, 300, "InteracaoIA",
             ["- tipo: TipoInteracaoIA", "- textoEntrada: String",
              "- jsonExtraido: String", "- sucesso: boolean", "- criadoEm: LocalDateTime"])
    d.classe("tipo", 540, 600, 260, "TipoInteracaoIA",
             ["EXTRACAO_EVENTO", "RESUMO_AVALIACAO"], estereotipo="enumeration")

    um_para_um = [("1", -0.82, 0, -11), ("0..1", 0.85, 22, 0)]
    d.aresta("assoc", "inscricao", "certificado", (0.5, 0), (0.5, 1),
             rotulos=[("1", -0.6, 14, 0), ("0..1", 0.55, 22, 0)])
    d.aresta("assoc", "inscricao", "presenca", (0, 0.5), (0.5, 1),
             pontos=[(210, 323)], rotulos=um_para_um)
    d.aresta("assoc", "inscricao", "avaliacao", (1, 0.5), (0.5, 1),
             pontos=[(1090, 323)], rotulos=um_para_um)
    d.aresta("assoc", "evento", "resumo", (0.5, 1), (0.5, 0),
             rotulos=[("1", -0.5, 14, 0), ("0..1", 0.45, 22, 0)])
    d.aresta("assoc", "usuario", "interacao", (0.5, 1), (0.5, 0),
             rotulos=[("1", -0.5, 14, 0), ("0..*", 0.45, 22, 0)])
    return d


# =========================================================================== estados

def estados_inscricao() -> Diagrama:
    d = Diagrama("A3a", "A.3a Estados — Inscrição", 1120, 690)
    d.no("inicial", "ini", 548, 48, 24, 24)
    d.no("estado", "espera", 170, 200, 200, 56, [("EM_ESPERA", "b")])
    d.no("estado", "conf", 750, 200, 200, 56, [("CONFIRMADA", "b")])
    d.no("estado", "canc", 460, 460, 200, 56, [("CANCELADA", "b")])

    d.aresta("transicao", "ini", "espera", (0, 0.5), (0.5, 0), pontos=[(270, 60)],
             rotulos=[("inscrever [evento lotado] · RN-01", -0.3, 0, -12)])
    d.aresta("transicao", "ini", "conf", (1, 0.5), (0.5, 0), pontos=[(850, 60)],
             rotulos=[("inscrever [há vaga] · RN-01", -0.3, 0, -12)])
    d.aresta("transicao", "espera", "conf", (1, 0.5), (0, 0.5),
             rotulos=[("promover [vaga liberada] · RN-02", 0, 0, -12)])
    d.aresta("transicao", "espera", "canc", (0.5, 1), (0, 0.5), pontos=[(270, 488)],
             rotulos=[("cancelar · RF-07", -0.25, 0, 0)])
    d.aresta("transicao", "conf", "canc", (0.5, 1), (1, 0.5), pontos=[(850, 488)],
             rotulos=[("cancelar · RF-07", -0.25, 0, 0)])
    d.no("nota", "n", 60, 560, 560, 90,
         ["Presença e certificado não são estados: são registros ligados",
          "a uma inscrição CONFIRMADA (RN-05).",
          "AUSENTE existe no enum, mas nenhum fluxo da versão atual o grava.",
          "Reinscrever-se depois de cancelar cria uma nova inscrição (RN-03)."])
    return d


def estados_evento() -> Diagrama:
    d = Diagrama("A3b", "A.3b Estados — Evento", 1160, 540)
    d.no("inicial", "ini", 40, 123, 24, 24)
    nomes = [("rasc", "RASCUNHO", 110), ("pub", "PUBLICADO", 380),
             ("and", "EM_ANDAMENTO", 650), ("enc", "ENCERRADO", 920)]
    for id, nome, x in nomes:
        d.no("estado", id, x, 110, 170, 50, [(nome, "b")])
    d.no("estado", "canc", 380, 340, 440, 50, [("CANCELADO", "b")])
    d.no("final", "fim", 993, 353, 24, 24)

    d.aresta("transicao", "ini", "rasc", (1, 0.5), (0, 0.5))
    d.aresta("transicao", "rasc", "pub", (1, 0.5), (0, 0.5), rotulos=[("publicar", 0, 0, -12)])
    d.aresta("transicao", "pub", "and", (1, 0.5), (0, 0.5), rotulos=[("iniciar", 0, 0, -12)])
    d.aresta("transicao", "and", "enc", (1, 0.5), (0, 0.5), rotulos=[("encerrar", 0, 0, -12)])
    d.aresta("transicao", "pub", "enc", ((500 - 380) / 170, 0), ((970 - 920) / 170, 0),
             pontos=[(500, 60), (970, 60)], rotulos=[("encerrar", 0, 0, -12)])
    d.aresta("transicao", "rasc", "canc", (0.5, 1), (0, 0.5), pontos=[(195, 365)],
             rotulos=[("cancelar", -0.35, 0, 0)])
    d.aresta("transicao", "pub", "canc", (0.5, 1), ((465 - 380) / 440, 0),
             rotulos=[("cancelar", 0, 0, 0)])
    d.aresta("transicao", "and", "canc", (0.5, 1), ((735 - 380) / 440, 0),
             rotulos=[("cancelar", 0, 0, 0)])
    d.aresta("transicao", "enc", "fim", (0.5, 1), (0.5, 0))
    d.aresta("transicao", "canc", "fim", (1, 0.5), (0, 0.5))
    d.no("nota", "n", 110, 440, 710, 54,
         ["Só PUBLICADO aceita inscrição (RN-09). ENCERRADO e CANCELADO são terminais:",
          "o evento não aceita mais edição nem novas inscrições."])
    return d


# =========================================================================== atividades

def atividades_cadastro_ia() -> Diagrama:
    d = Diagrama("A4", "A.4 Atividades — Cadastro de evento assistido por IA", 1180, 770)
    d.no("raia", "r1", 30, 20, 360, 730, ["Organizador"], z=0)
    d.no("raia", "r2", 390, 20, 440, 730, ["Sistema"], z=0)
    d.no("raia", "r3", 830, 20, 320, 730, ["Modelo de linguagem"], z=0)

    d.no("inicial", "ini", 188, 76, 24, 24)
    d.no("acao", "colar", 110, 124, 180, 50, ["Colar o texto do evento"])
    d.no("acao", "enviar", 520, 119, 200, 60, ["Enviar texto ao modelo", ("rotação entre modelos", "m")])
    d.no("acao", "extrair", 900, 124, 180, 50, ["Extrair campos em JSON"])
    d.no("decisao", "ok", 555, 220, 130, 80, ["Resposta", "válida?"])
    d.no("acao", "reg_ok", 405, 340, 180, 54, ["Registrar interação", ("sucesso", "m")])
    d.no("acao", "reg_falha", 635, 340, 180, 54, ["Registrar interação", ("falha · responde 503", "m")])
    d.no("acao", "revisar", 110, 340, 180, 54, ["Revisar campos", "sugeridos"])
    d.no("acao", "manual", 110, 440, 180, 54, ["Preencher", "formulário manual"])
    d.no("decisao", "junta", 180, 530, 40, 40)
    d.no("acao", "salvar", 110, 600, 180, 50, ["Salvar evento"])
    d.no("acao", "gravar", 530, 598, 180, 54, ["Gravar evento", ("situação RASCUNHO", "m")])
    d.no("final", "fim", 608, 690, 24, 24)

    f = "fluxo"
    d.aresta(f, "ini", "colar", (0.5, 1), (0.5, 0))
    d.aresta(f, "colar", "enviar", (1, 0.5), (0, 0.5))
    d.aresta(f, "enviar", "extrair", (1, 0.5), (0, 0.5))
    d.aresta(f, "extrair", "ok", (0.5, 1), (0.5, 0), pontos=[(990, 200), (620, 200)])
    d.aresta(f, "ok", "reg_ok", (0, 0.5), (0.5, 0), pontos=[(495, 260)],
             rotulos=[Rotulo("[sim]", -0.45, 0, -11, destaque=True)])
    d.aresta(f, "ok", "reg_falha", (1, 0.5), (0.5, 0), pontos=[(725, 260)],
             rotulos=[Rotulo("[não]", -0.45, 0, -11, destaque=True)])
    d.aresta(f, "reg_ok", "revisar", (0, 0.5), (1, 0.5))
    d.aresta(f, "reg_falha", "manual", (0.5, 1), (1, 0.5), pontos=[(725, 467)])
    d.aresta(f, "revisar", "junta", (0, 0.5), (0, 0.5), pontos=[(80, 367), (80, 550)])
    d.aresta(f, "manual", "junta", (0.5, 1), (0.5, 0))
    d.aresta(f, "junta", "salvar", (0.5, 1), (0.5, 0))
    d.aresta(f, "salvar", "gravar", (1, 0.5), (0, 0.5))
    d.aresta(f, "gravar", "fim", (0.5, 1), (0.5, 0))
    d.no("nota", "n", 850, 540, 285, 84,
         ["RN-10: se o modelo falhar, o",
          "cadastro manual continua",
          "disponível. Nenhum evento é gravado",
          "sem a revisão do organizador."])
    return d


# =========================================================================== sequencia

class Sequencia:
    """Linhas de vida, ativacoes, mensagens e fragmentos sobre um Diagrama."""

    def __init__(self, d: Diagrama, participantes, fim: int):
        self.d, self.fim, self.x, self.ativas = d, fim, {}, {}
        for chave, x, texto, w, ator in participantes:
            self.x[chave] = x
            self.ativas[chave] = []
            if ator:
                d.no("ator_seq", chave, x - 20, 24, 40, 60, texto, z=1, extra={"fim": fim})
            else:
                d.no("cabeca", chave, x - w / 2, 30, w, 44, texto, z=1, extra={"fim": fim})

    def ativa(self, chave, y0, y1):
        self.ativas[chave].append((y0, y1))
        self.d.no("ativacao", f"{chave}-at{len(self.ativas[chave])}", self.x[chave] - 6, y0,
                  12, y1 - y0, z=3)

    def _borda(self, chave, y, direcao):
        ativo = any(a - 1 <= y <= b + 1 for a, b in self.ativas[chave])
        return self.x[chave] + (6 * direcao if ativo else 0)

    def msg(self, n, de, para, texto, y, retorno=False):
        sentido = 1 if self.x[para] > self.x[de] else -1
        p0 = (self._borda(de, y, sentido), y)
        p1 = (self._borda(para, y, -sentido), y)
        rotulo = f"{n}: {texto}"
        # Rotulo mais largo que o vao invade a barra de ativacao vizinha. Melhor acusar aqui
        # do que descobrir olhando a imagem.
        vao = abs(p1[0] - p0[0]) - 8
        if largura_texto(rotulo, 11.5) > vao:
            print(f"  aviso {self.d.codigo}: '{rotulo}' nao cabe em {vao:.0f}px")
        self.d.livre("retorno" if retorno else "msg", p0, p1, rotulo)

    def fragmento(self, rotulo, x0, x1, y0, y1, guardas, separadores=(), gx=None):
        """gx posiciona as guardas; por padrao, logo depois da etiqueta do fragmento."""
        self.d.no("fragmento", self.d._id("frag"), x0, y0, x1 - x0, y1 - y0, [rotulo], z=2)
        x_guarda = gx if gx is not None else x0 + 34 + largura_texto(rotulo, 12)
        for y, texto in guardas:
            self.d.no("guarda", self.d._id("g"), x_guarda, y + 3, 10, 16, [texto], z=4)
        for y in separadores:
            self.d.livre("separador", (x0, y), (x1, y), z=2)


def sequencia_cadastro_ia() -> Diagrama:
    d = Diagrama("A5", "A.5 Sequência — Cadastro de evento via IA", 1600, 760)
    s = Sequencia(d, [
        ("org", 70, ["Organizador"], 40, True),
        ("front", 260, [("Frontend", "b"), ("React", "m")], 150, False),
        ("ctrl", 510, [("EventoController", "b")], 170, False),
        ("assist", 810, [("AssistenteIA", "b")], 150, False),
        ("extr", 1050, [("ExtratorEvento", "b"), ("ChamadaDeModelo", "m")], 170, False),
        ("llm", 1280, [("Modelo de linguagem", "b"), ("Groq", "m")], 170, False),
        ("reg", 1490, [("InteracaoIARegistro", "b")], 170, False),
    ], fim=730)
    s.ativa("front", 112, 688)
    s.ativa("ctrl", 144, 634)
    s.ativa("assist", 176, 602)
    s.ativa("extr", 208, 538)
    s.ativa("llm", 274, 358)
    s.ativa("reg", 406, 422)
    s.ativa("reg", 554, 570)

    s.msg(1, "org", "front", "colar texto e interpretar", 120)
    s.msg(2, "front", "ctrl", "POST /api/eventos/interpretar", 152)
    s.msg(3, "ctrl", "assist", "interpretarEvento(usuarioId, texto)", 184)
    s.msg(4, "assist", "extr", "extrair(texto)", 216)
    s.fragmento("loop", 990, 1370, 232, 300, [(232, "[até um modelo responder]")],
                gx=1064)
    s.msg(5, "extr", "llm", "prompt + esquema JSON", 282)
    s.fragmento("alt", 170, 1580, 314, 648,
                [(314, "[modelo respondeu]"),
                 (494, "[falha ou tempo esgotado]")], separadores=[494],
                gx=274)
    s.msg(6, "llm", "extr", "JSON", 350, retorno=True)
    s.msg(7, "extr", "assist", "EventoExtraidoDTO", 382, retorno=True)
    s.msg(8, "assist", "reg", "registrar(sucesso = true)", 414)
    s.msg(9, "assist", "ctrl", "rascunho", 446, retorno=True)
    s.msg(10, "ctrl", "front", "200 OK", 478, retorno=True)
    s.msg(11, "extr", "assist", "IaIndisponivelException", 530, retorno=True)
    s.msg(12, "assist", "reg", "registrar(sucesso = false)", 562)
    s.msg(13, "assist", "ctrl", "IaIndisponivelException", 594, retorno=True)
    s.msg(14, "ctrl", "front", "503 IA_INDISPONIVEL", 626, retorno=True)
    s.msg(15, "front", "org", "revisar ou preencher", 680, retorno=True)
    return d


def sequencia_emissao_certificado() -> Diagrama:
    d = Diagrama("A6a", "A.6a Sequência — Emissão do certificado", 1400, 760)
    s = Sequencia(d, [
        ("part", 70, ["Participante"], 40, True),
        ("front", 270, [("Frontend", "b"), ("React", "m")], 150, False),
        ("ctrl", 540, [("CertificadoController", "b")], 180, False),
        ("serv", 800, [("CertificadoService", "b")], 170, False),
        ("crepo", 1050, [("CertificadoRepository", "b")], 180, False),
        ("prepo", 1290, [("PresencaRepository", "b")], 170, False),
    ], fim=730)
    s.ativa("front", 112, 698)
    s.ativa("ctrl", 144, 666)
    s.ativa("serv", 176, 634)
    s.ativa("crepo", 208, 280)
    s.ativa("crepo", 532, 580)
    s.ativa("prepo", 318, 366)
    s.ativa("prepo", 468, 516)

    s.msg(1, "part", "front", "emitir certificado", 120)
    s.msg(2, "front", "ctrl", "POST /api/inscricoes/{id}/certificado", 152)
    s.msg(3, "ctrl", "serv", "emitir(inscricaoId, usuarioId)", 184)
    s.msg(4, "serv", "crepo", "buscarPorInscricao(inscricaoId)", 216)
    s.fragmento("alt", 170, 1385, 236, 600,
                [(236, "[certificado já emitido]"),
                 (290, "[ainda não emitido, sem presença]"),
                 (440, "[ainda não emitido, com presença]")], separadores=[290, 440],
                gx=284)
    s.msg(5, "crepo", "serv", "certificado existente (idempotente)", 272, retorno=True)
    s.msg(6, "serv", "prepo", "existsByInscricaoId(inscricaoId)", 326)
    s.msg(7, "prepo", "serv", "false", 358, retorno=True)
    s.msg(8, "serv", "ctrl", "PresencaNaoRegistradaException", 390, retorno=True)
    s.msg(9, "ctrl", "front", "422 PRESENCA_NAO_REGISTRADA", 422, retorno=True)
    s.msg(10, "serv", "prepo", "existsByInscricaoId(inscricaoId)", 476)
    s.msg(11, "prepo", "serv", "true", 508, retorno=True)
    s.msg(12, "serv", "crepo", "save(new Certificado(inscricao))", 540)
    s.msg(13, "crepo", "serv", "certificado com código", 572, retorno=True)
    s.msg(14, "serv", "ctrl", "CertificadoResponse", 626, retorno=True)
    s.msg(15, "ctrl", "front", "201 Created", 658, retorno=True)
    s.msg(16, "front", "part", "código e link", 690, retorno=True)
    return d


def sequencia_download_certificado() -> Diagrama:
    d = Diagrama("A6b", "A.6b Sequência — Download do certificado em PDF", 1400, 540)
    s = Sequencia(d, [
        ("part", 70, ["Participante"], 40, True),
        ("front", 270, [("Frontend", "b"), ("React", "m")], 150, False),
        ("ctrl", 540, [("CertificadoController", "b")], 180, False),
        ("serv", 800, [("CertificadoService", "b")], 170, False),
        ("crepo", 1050, [("CertificadoRepository", "b")], 180, False),
        ("ger", 1290, [("GeradorCertificado", "b"), ("iText", "m")], 170, False),
    ], fim=440)
    s.ativa("front", 112, 416)
    s.ativa("ctrl", 144, 384)
    s.ativa("serv", 176, 352)
    s.ativa("crepo", 208, 256)
    s.ativa("ger", 272, 320)

    s.msg(1, "part", "front", "baixar certificado", 120)
    s.msg(2, "front", "ctrl", "GET /api/inscricoes/{id}/certificado", 152)
    s.msg(3, "ctrl", "serv", "gerarPdf(inscricaoId, usuarioId)", 184)
    s.msg(4, "serv", "crepo", "buscarPorInscricao(inscricaoId)", 216)
    s.msg(5, "crepo", "serv", "certificado", 248, retorno=True)
    s.msg(6, "serv", "ger", "gerar(dados)", 280)
    s.msg(7, "ger", "serv", "byte[] — PDF gerado em memória", 312, retorno=True)
    s.msg(8, "serv", "ctrl", "byte[]", 344, retorno=True)
    s.msg(9, "ctrl", "front", "200 application/pdf", 376, retorno=True)
    s.msg(10, "front", "part", "exibe o certificado", 408, retorno=True)
    d.no("nota", "n", 760, 462, 600, 48,
         ["Só o titular da inscrição baixa o certificado (RN-08).",
          "O PDF é montado a cada download e não é gravado em disco."])
    return d


# =========================================================================== componentes

def componentes() -> Diagrama:
    d = Diagrama("A7", "A.7 Componentes", 1440, 680)
    d.no("componente", "front", 30, 59, 220, 100,
         [("«component»", "m"), ("Frontend SPA", "b"), ("React · TypeScript", "m"),
          ("Vercel", "m")])
    d.no("container", "back", 420, 40, 980, 450, ["Backend — Spring Boot 4 · Render"], z=0)
    d.no("componente", "seg", 450, 86, 920, 46,
         [("Segurança — filtro JWT e Spring Security", "b")])
    modulos = ["Usuários", "Eventos", "Inscrições", "Presença", "Avaliações", "Certificados"]
    for i, nome in enumerate(modulos):
        d.no("componente", f"m{i}", 450 + i * 156, 180, 140, 64, [("«component»", "m"),
                                                                  (nome, "b")])
    d.no("componente", "arm", 600, 380, 150, 64, [("«component»", "m"), ("Armazenamento", "b")])
    d.no("componente", "ia", 870, 380, 150, 64, [("«component»", "m"), ("IA", "b")])
    d.no("componente", "itext", 1225, 380, 150, 64, [("«library»", "m"), ("iText", "b")])
    d.no("banco", "db", 470, 560, 180, 90, [("PostgreSQL 16", "b"), ("Neon", "m")])
    d.no("externo", "groq", 855, 560, 180, 70, [("API Groq", "b"), ("modelos de linguagem", "m")])

    d.aresta("uso", "front", "seg", (1, 0.5), (0, 0.5),
             rotulos=[("REST + JSON · JWT", 0, 0, -12)])
    d.aresta("opcional", "m1", "ia", (0.8, 1), ((900 - 870) / 150, 0),
             pontos=[(450 + 156 + 112, 320), (900, 320)],
             rotulos=[("opcional · RN-10", 0, 0, -11)])
    d.aresta("opcional", "m4", "ia", (0.3, 1), ((990 - 870) / 150, 0),
             pontos=[(450 + 4 * 156 + 42, 320), (990, 320)])
    d.aresta("uso", "m1", "arm", (0.3, 1), ((450 + 156 + 42 - 600) / 150, 0))
    d.aresta("uso", "m5", "itext", (0.5, 1), ((450 + 5 * 156 + 70 - 1225) / 150, 0))
    d.aresta("uso", "ia", "groq", (0.5, 1), ((945 - 855) / 180, 0),
             rotulos=[("HTTPS", 0, 30, 0)])
    d.aresta("uso", "back", "db", ((560 - 420) / 980, 1), (0.5, 0),
             rotulos=[("JPA · Flyway", 0, 46, 0)])
    d.no("nota", "n", 1110, 560, 290, 64,
         ["Linha tracejada: dependência opcional.",
          "Sem IA, o sistema segue funcionando",
          "com cadastro manual (RN-10)."])
    return d


TODOS = [casos_participante, casos_organizador, classes_nucleo, classes_participacao,
         estados_inscricao, estados_evento, atividades_cadastro_ia, sequencia_cadastro_ia,
         sequencia_emissao_certificado, sequencia_download_certificado, componentes]

ARQUIVOS = {
    "A1a": "A1a-casos-de-uso-participante",
    "A1b": "A1b-casos-de-uso-organizador",
    "A2a": "A2a-classes-nucleo",
    "A2b": "A2b-classes-participacao-ia",
    "A3a": "A3a-estados-inscricao",
    "A3b": "A3b-estados-evento",
    "A4": "A4-atividades-cadastro-ia",
    "A5": "A5-sequencia-cadastro-ia",
    "A6a": "A6a-sequencia-emissao-certificado",
    "A6b": "A6b-sequencia-download-certificado",
    "A7": "A7-componentes",
}
