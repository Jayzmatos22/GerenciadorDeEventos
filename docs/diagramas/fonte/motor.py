"""Motor dos diagramas: um modelo de geometria, duas saidas (.drawio e SVG).

Cada diagrama e descrito uma unica vez, com coordenadas absolutas. Dessa descricao saem o
arquivo .drawio, editavel no draw.io, e o SVG usado para gerar PNG e PDF. Como as duas saidas
leem a mesma geometria, o que se ve na imagem e o que se abre no editor.

// DECISAO: as arestas sao ligadas aos nos com pontos de saida e entrada fixados (exitX/entryX)
e perimetro desligado. Assim o draw.io nao recalcula o trajeto ao abrir o arquivo, e a imagem
gerada aqui coincide com a do editor; mover um no ainda arrasta a aresta junto.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from xml.sax.saxutils import escape, quoteattr

FONTE = "Helvetica, Arial, 'Liberation Sans', sans-serif"
ALTURA_LINHA = 18
CABECALHO = 30
CABECALHO_ESTEREOTIPO = 44


# --------------------------------------------------------------------------- texto

def largura_texto(texto: str, tamanho: float) -> float:
    """Estimativa da largura em Helvetica. Basta para dimensionar fundo de rotulo."""
    total = 0.0
    for c in texto:
        if c in "ilj.,:;|!'()[]{}":
            total += 0.28
        elif c in "mwMW":
            total += 0.86
        elif c == " ":
            total += 0.28
        elif c.isupper():
            total += 0.67
        elif c.isdigit():
            total += 0.56
        else:
            total += 0.53
    return total * tamanho


@dataclass
class Linha:
    """Uma linha de texto com estilo: b negrito, u sublinhado (membro estatico), m discreto."""
    texto: str
    estilo: str = ""


def linhas(*itens) -> list[Linha]:
    resultado = []
    for item in itens:
        if isinstance(item, Linha):
            resultado.append(item)
        elif isinstance(item, tuple):
            resultado.append(Linha(*item))
        else:
            resultado.append(Linha(item))
    return resultado


def html_linhas(itens: list[Linha], tema: dict) -> str:
    partes = []
    for l in itens:
        t = escape(l.texto)
        if "u" in l.estilo:
            t = f"<u>{t}</u>"
        if "b" in l.estilo:
            t = f"<b>{t}</b>"
        if "m" in l.estilo:
            t = f'<span style="font-size:11px;color:{tema["mudo"]}">{t}</span>'
        partes.append(t)
    return "<br>".join(partes)


# --------------------------------------------------------------------------- modelo

@dataclass
class No:
    id: str
    tipo: str
    x: float
    y: float
    w: float
    h: float
    texto: list = field(default_factory=list)
    atributos: list = field(default_factory=list)
    operacoes: list = field(default_factory=list)
    estereotipo: str = ""
    z: int = 3
    extra: dict = field(default_factory=dict)

    def ponto(self, fx: float, fy: float, dy: float = 0) -> tuple[float, float]:
        return (self.x + self.w * fx, self.y + self.h * fy + dy)


@dataclass
class Rotulo:
    texto: str
    pos: float = 0.0      # -1 na origem, 1 no destino, como no draw.io
    dx: float = 0.0
    dy: float = 0.0
    destaque: bool = False


@dataclass
class Aresta:
    id: str
    tipo: str
    origem: str | None = None
    destino: str | None = None
    saida: tuple = (0.5, 0.5)
    entrada: tuple = (0.5, 0.5)
    dy_entrada: float = 0
    pontos: list = field(default_factory=list)
    p0: tuple | None = None
    p1: tuple | None = None
    rotulos: list = field(default_factory=list)
    z: int = 2


# tipo de aresta -> (tracejada, ponta no destino, ponta na origem)
TIPOS_ARESTA = {
    "assoc": (False, None, None),
    "composicao": (False, None, "losango"),
    "heranca": (False, "vazada", None),
    "include": (True, "aberta", None),
    "extend": (True, "aberta", None),
    "transicao": (False, "cheia", None),
    "fluxo": (False, "cheia", None),
    "uso": (False, "aberta", None),
    "opcional": (True, "aberta", None),
    "msg": (False, "cheia", None),
    "retorno": (True, "aberta", None),
    "separador": (True, None, None),
}


class Diagrama:
    def __init__(self, codigo: str, titulo: str, largura: int, altura: int):
        self.codigo = codigo
        self.titulo = titulo
        self.largura = largura
        self.altura = altura
        self.nos: dict[str, No] = {}
        self.ordem: list = []
        self._seq = 0

    # ---- criacao de elementos ------------------------------------------------
    def _id(self, base: str) -> str:
        self._seq += 1
        return f"{self.codigo}-{base}-{self._seq}"

    def no(self, tipo, id, x, y, w, h, texto=(), **kw) -> No:
        n = No(id=id, tipo=tipo, x=x, y=y, w=w, h=h, texto=linhas(*texto), **kw)
        self.nos[id] = n
        self.ordem.append(n)
        return n

    def classe(self, id, x, y, w, nome, atributos=(), operacoes=(), estereotipo=""):
        attrs = linhas(*atributos)
        ops = linhas(*operacoes)
        h = CABECALHO_ESTEREOTIPO if estereotipo else CABECALHO
        if attrs:
            h += len(attrs) * ALTURA_LINHA + 10
        if ops:
            h += 8 + len(ops) * ALTURA_LINHA + 10
        return self.no("classe", id, x, y, w, h, [Linha(nome, "b")],
                       atributos=attrs, operacoes=ops, estereotipo=estereotipo)

    def aresta(self, tipo, origem, destino, saida, entrada, pontos=(), rotulos=(),
               dy_entrada=0) -> Aresta:
        a = Aresta(id=self._id("e"), tipo=tipo, origem=origem, destino=destino,
                   saida=saida, entrada=entrada, pontos=list(pontos),
                   rotulos=[r if isinstance(r, Rotulo) else Rotulo(*r) for r in rotulos],
                   dy_entrada=dy_entrada)
        self.ordem.append(a)
        return a

    def livre(self, tipo, p0, p1, rotulo="", z=4) -> Aresta:
        a = Aresta(id=self._id("m"), tipo=tipo, p0=p0, p1=p1,
                   rotulos=[Rotulo(rotulo)] if rotulo else [], z=z)
        self.ordem.append(a)
        return a

    # ---- geometria -----------------------------------------------------------
    def trajeto(self, a: Aresta) -> list[tuple[float, float]]:
        if a.p0 is not None:
            return [a.p0, a.p1]
        inicio = self.nos[a.origem].ponto(*a.saida)
        fim = self.nos[a.destino].ponto(*a.entrada, dy=a.dy_entrada)
        return [inicio, *a.pontos, fim]


def ponto_no_trajeto(pts, pos):
    """Ponto a uma fracao (pos de -1 a 1) do comprimento total, como o draw.io posiciona."""
    segs = [(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
    comprimentos = [math.dist(a, b) for a, b in segs]
    alvo = (pos + 1) / 2 * sum(comprimentos)
    for (a, b), c in zip(segs, comprimentos):
        if alvo <= c or c == comprimentos[-1] and (a, b) == segs[-1]:
            t = 0 if c == 0 else min(alvo / c, 1)
            return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        alvo -= c
    return pts[-1]
