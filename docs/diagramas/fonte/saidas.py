"""As duas saidas do motor: SVG (para PNG e PDF) e .drawio (para edicao)."""
from __future__ import annotations

import math
from xml.sax.saxutils import escape, quoteattr

from motor import (ALTURA_LINHA, CABECALHO, CABECALHO_ESTEREOTIPO, FONTE, TIPOS_ARESTA,
                   Aresta, Diagrama, Linha, No, html_linhas, largura_texto,
                   ponto_no_trajeto)


# =========================================================================== SVG

def _txt(x, y, texto, tema, tam=12, cor=None, peso="normal", ancora="middle",
         sublinhado=False):
    deco = ' text-decoration="underline"' if sublinhado else ""
    return (f'<text x="{x:.1f}" y="{y:.1f}" font-family="{FONTE}" font-size="{tam}" '
            f'font-weight="{peso}" fill="{cor or tema["texto"]}" text-anchor="{ancora}"'
            f'{deco}>{escape(texto)}</text>')


def _bloco_centrado(n: No, tema, cy=None, tam=12):
    """Linhas centradas na caixa; estilo m fica menor e discreto, b fica em negrito."""
    alturas = [15 if "m" in l.estilo else 17 for l in n.texto]
    total = sum(alturas)
    y = (cy if cy is not None else n.y + n.h / 2) - total / 2
    partes = []
    for l, a in zip(n.texto, alturas):
        y += a
        discreto = "m" in l.estilo
        partes.append(_txt(n.x + n.w / 2, y - 4, l.texto, tema,
                           tam=11 if discreto else tam,
                           cor=tema["mudo"] if discreto else None,
                           peso="bold" if "b" in l.estilo else "normal",
                           sublinhado="u" in l.estilo))
    return "".join(partes)


def _rect(x, y, w, h, fill, stroke, sw=1.4, rx=0, dash=False):
    d = ' stroke-dasharray="6 4"' if dash else ""
    return (f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{d}/>')


def _boneco(cx, top, cor):
    """Ator UML: 40 px de altura a partir de top."""
    return (f'<g stroke="{cor}" stroke-width="1.6" fill="none" stroke-linecap="round">'
            f'<circle cx="{cx}" cy="{top + 7}" r="7"/>'
            f'<line x1="{cx}" y1="{top + 14}" x2="{cx}" y2="{top + 29}"/>'
            f'<line x1="{cx - 12}" y1="{top + 19}" x2="{cx + 12}" y2="{top + 19}"/>'
            f'<line x1="{cx}" y1="{top + 29}" x2="{cx - 10}" y2="{top + 40}"/>'
            f'<line x1="{cx}" y1="{top + 29}" x2="{cx + 10}" y2="{top + 40}"/></g>')


def svg_no(n: No, t: dict) -> str:
    sup, cab, borda = t["superficie"], t["cabecalho"], t["borda"]
    if n.tipo == "classe":
        head = CABECALHO_ESTEREOTIPO if n.estereotipo else CABECALHO
        s = _rect(n.x, n.y, n.w, n.h, sup, borda)
        s += _rect(n.x, n.y, n.w, head, cab, borda)
        cx = n.x + n.w / 2
        if n.estereotipo:
            s += _txt(cx, n.y + 17, f"«{n.estereotipo}»", t, tam=11, cor=t["mudo"])
            s += _txt(cx, n.y + 35, n.texto[0].texto, t, tam=13, peso="bold")
        else:
            s += _txt(cx, n.y + 20, n.texto[0].texto, t, tam=13, peso="bold")
        y = n.y + head
        for bloco_i, bloco in enumerate((n.atributos, n.operacoes)):
            if not bloco:
                continue
            if bloco_i == 1 and n.atributos:
                s += (f'<line x1="{n.x}" y1="{y + 4:.1f}" x2="{n.x + n.w}" y2="{y + 4:.1f}" '
                      f'stroke="{borda}" stroke-width="1.2"/>')
                y += 8
            for i, l in enumerate(bloco):
                s += _txt(n.x + 10, y + 18 + i * ALTURA_LINHA, l.texto, t, tam=12,
                          ancora="start", sublinhado="u" in l.estilo,
                          cor=t["texto"])
            y += len(bloco) * ALTURA_LINHA + 10
        return s
    if n.tipo == "referencia":
        return _rect(n.x, n.y, n.w, n.h, sup, t["mudo"], dash=True) + _bloco_centrado(n, t)
    if n.tipo == "caso":
        return (f'<ellipse cx="{n.x + n.w / 2}" cy="{n.y + n.h / 2}" rx="{n.w / 2}" '
                f'ry="{n.h / 2}" fill="{sup}" stroke="{borda}" stroke-width="1.4"/>'
                + _bloco_centrado(n, t))
    if n.tipo == "ator":
        s = _boneco(n.x + n.w / 2, n.y + 6, t["destaque"])
        y = n.y + n.h + 14
        for l in n.texto:
            discreto = "m" in l.estilo
            s += _txt(n.x + n.w / 2, y, l.texto, t, tam=11 if discreto else 12.5,
                      cor=t["mudo"] if discreto else None,
                      peso="normal" if discreto else "bold")
            y += 15
        return s
    if n.tipo == "estado":
        return _rect(n.x, n.y, n.w, n.h, sup, t["destaque"], sw=1.6, rx=14) + \
            _bloco_centrado(n, t)
    if n.tipo == "inicial":
        r = n.w / 2
        return f'<circle cx="{n.x + r}" cy="{n.y + r}" r="{r}" fill="{t["texto"]}"/>'
    if n.tipo == "final":
        r = n.w / 2
        return (f'<circle cx="{n.x + r}" cy="{n.y + r}" r="{r - 0.8}" fill="none" '
                f'stroke="{t["texto"]}" stroke-width="1.6"/>'
                f'<circle cx="{n.x + r}" cy="{n.y + r}" r="{r - 5}" fill="{t["texto"]}"/>')
    if n.tipo == "acao":
        return _rect(n.x, n.y, n.w, n.h, sup, borda, rx=16) + _bloco_centrado(n, t)
    if n.tipo == "decisao":
        cx, cy = n.x + n.w / 2, n.y + n.h / 2
        pts = f"{cx},{n.y} {n.x + n.w},{cy} {cx},{n.y + n.h} {n.x},{cy}"
        return (f'<polygon points="{pts}" fill="{sup}" stroke="{t["destaque"]}" '
                f'stroke-width="1.6"/>' + _bloco_centrado(n, t, tam=11.5))
    if n.tipo == "fronteira":
        return (_rect(n.x, n.y, n.w, n.h, "none", borda, sw=1.4)
                + _txt(n.x + n.w / 2, n.y + 24, n.texto[0].texto, t, tam=13,
                       cor=t["mudo"], peso="bold"))
    if n.tipo == "raia":
        return (_rect(n.x, n.y, n.w, n.h, "none", borda)
                + _rect(n.x, n.y, n.w, 36, cab, borda)
                + _txt(n.x + n.w / 2, n.y + 23, n.texto[0].texto, t, tam=13, peso="bold"))
    if n.tipo in ("componente", "externo"):
        return _rect(n.x, n.y, n.w, n.h, sup, borda if n.tipo == "componente" else t["mudo"],
                     dash=n.tipo == "externo") + _bloco_centrado(n, t)
    if n.tipo == "container":
        return (_rect(n.x, n.y, n.w, n.h, t["container"], borda, sw=1.4)
                + _txt(n.x + 16, n.y + 24, n.texto[0].texto, t, tam=13, ancora="start",
                       peso="bold", cor=t["mudo"]))
    if n.tipo == "banco":
        e = 10
        cx, rx = n.x + n.w / 2, n.w / 2
        corpo = (f'<path d="M{n.x},{n.y + e} L{n.x},{n.y + n.h - e} '
                 f'A{rx},{e} 0 0 0 {n.x + n.w},{n.y + n.h - e} L{n.x + n.w},{n.y + e}" '
                 f'fill="{sup}" stroke="{borda}" stroke-width="1.4"/>')
        tampa = (f'<ellipse cx="{cx}" cy="{n.y + e}" rx="{rx}" ry="{e}" fill="{cab}" '
                 f'stroke="{borda}" stroke-width="1.4"/>')
        return corpo + tampa + _bloco_centrado(n, t, cy=n.y + n.h / 2 + 6)
    if n.tipo == "nota":
        d = 14
        pts = (f"{n.x},{n.y} {n.x + n.w - d},{n.y} {n.x + n.w},{n.y + d} "
               f"{n.x + n.w},{n.y + n.h} {n.x},{n.y + n.h}")
        s = (f'<polygon points="{pts}" fill="{t["nota"]}" stroke="{t["mudo"]}" '
             f'stroke-width="1.1"/>'
             f'<polyline points="{n.x + n.w - d},{n.y} {n.x + n.w - d},{n.y + d} '
             f'{n.x + n.w},{n.y + d}" fill="none" stroke="{t["mudo"]}" stroke-width="1.1"/>')
        y = n.y + 20
        for l in n.texto:
            s += _txt(n.x + 12, y, l.texto, t, tam=11.5, cor=t["mudo"], ancora="start")
            y += 16
        return s
    if n.tipo == "cabeca":
        s = (f'<line x1="{n.x + n.w / 2}" y1="{n.y + n.h}" x2="{n.x + n.w / 2}" '
             f'y2="{n.extra["fim"]}" stroke="{t["mudo"]}" stroke-width="1.1" '
             f'stroke-dasharray="5 4"/>')
        return s + _rect(n.x, n.y, n.w, n.h, cab, borda) + _bloco_centrado(n, t)
    if n.tipo == "ator_seq":
        cx = n.x + n.w / 2
        s = (f'<line x1="{cx}" y1="{n.y + 62}" x2="{cx}" y2="{n.extra["fim"]}" '
             f'stroke="{t["mudo"]}" stroke-width="1.1" stroke-dasharray="5 4"/>')
        s += _boneco(cx, n.y, t["destaque"])
        return s + _txt(cx, n.y + 56, n.texto[0].texto, t, tam=12.5, peso="bold")
    if n.tipo == "ativacao":
        return _rect(n.x, n.y, n.w, n.h, cab, borda, sw=1.2)
    if n.tipo == "fragmento":
        rotulo = n.texto[0].texto
        lw, lh = 22 + largura_texto(rotulo, 12), 22
        tag = (f"{n.x},{n.y} {n.x + lw},{n.y} {n.x + lw},{n.y + lh - 7} "
               f"{n.x + lw - 7},{n.y + lh} {n.x},{n.y + lh}")
        return (_rect(n.x, n.y, n.w, n.h, "none", t["mudo"], sw=1.2)
                + f'<polygon points="{tag}" fill="{cab}" stroke="{t["mudo"]}" '
                  f'stroke-width="1.2"/>'
                + _txt(n.x + 9, n.y + 15.5, rotulo, t, tam=12, cor=t["destaque"],
                       peso="bold", ancora="start"))
    if n.tipo == "guarda":
        w = largura_texto(n.texto[0].texto, 11.5) + 8
        return (f'<rect x="{n.x - 4:.1f}" y="{n.y - 1:.1f}" width="{w:.1f}" height="17" '
                f'fill="{t["fundo"]}"/>'
                + _txt(n.x, n.y + 12, n.texto[0].texto, t, tam=11.5, cor=t["destaque"],
                       ancora="start"))
    raise ValueError(f"tipo de no desconhecido: {n.tipo}")


def _ponta(tipo, ponta, base, t):
    """Desenha a ponta de seta no ponto `ponta`, vindo da direcao de `base`."""
    (x1, y1), (x0, y0) = ponta, base
    ang = math.atan2(y1 - y0, x1 - x0)
    def p(dist, lado):
        return (x1 - dist * math.cos(ang) - lado * math.sin(ang),
                y1 - dist * math.sin(ang) + lado * math.cos(ang))
    cor = t["linha"]
    if tipo == "cheia":
        a, b = p(10, 4.5), p(10, -4.5)
        return f'<polygon points="{x1},{y1} {a[0]},{a[1]} {b[0]},{b[1]}" fill="{cor}"/>'
    if tipo == "aberta":
        a, b = p(10, 5), p(10, -5)
        return (f'<polyline points="{a[0]},{a[1]} {x1},{y1} {b[0]},{b[1]}" fill="none" '
                f'stroke="{cor}" stroke-width="1.3"/>')
    if tipo == "vazada":
        a, b = p(15, 8), p(15, -8)
        return (f'<polygon points="{x1},{y1} {a[0]},{a[1]} {b[0]},{b[1]}" '
                f'fill="{t["fundo"]}" stroke="{cor}" stroke-width="1.3"/>')
    if tipo == "losango":
        a, m, b = p(8, 5.5), p(16, 0), p(8, -5.5)
        return (f'<polygon points="{x1},{y1} {a[0]},{a[1]} {m[0]},{m[1]} {b[0]},{b[1]}" '
                f'fill="{cor}" stroke="{cor}" stroke-width="1"/>')
    return ""


def svg_aresta(d: Diagrama, a: Aresta, t) -> tuple[str, str]:
    """Devolve (linha, rotulos): os rotulos vao por cima de tudo."""
    pts = d.trajeto(a)
    tracejada, fim, inicio = TIPOS_ARESTA[a.tipo]
    dash = ' stroke-dasharray="6 4"' if tracejada else ""
    cor = t["mudo"] if a.tipo == "separador" else t["linha"]
    s = (f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y in pts)}" fill="none" '
         f'stroke="{cor}" stroke-width="1.3"{dash}/>')
    if fim:
        s += _ponta(fim, pts[-1], pts[-2], t)
    if inicio:
        s += _ponta(inicio, pts[0], pts[1], t)

    rot = ""
    mensagem = a.tipo in ("msg", "retorno")
    for r in a.rotulos:
        x, y = ponto_no_trajeto(pts, r.pos)
        x, y = x + r.dx, y + r.dy + (-9 if mensagem else 0)
        tam = 11.5
        w = largura_texto(r.texto, tam) + 8
        cor_t = t["destaque"] if r.destaque else t["texto"]
        if mensagem:
            # Fundo para a linha de vida tracejada nao riscar o texto.
            rot += (f'<rect x="{x - w / 2:.1f}" y="{y - 12:.1f}" width="{w:.1f}" height="16" '
                    f'fill="{t["fundo"]}"/>' + _txt(x, y, r.texto, t, tam=tam, cor=cor_t))
        else:
            rot += (f'<rect x="{x - w / 2:.1f}" y="{y - 9:.1f}" width="{w:.1f}" height="16" '
                    f'fill="{t["fundo"]}"/>' + _txt(x, y + 3.5, r.texto, t, tam=tam,
                                                     cor=cor_t))
    return s, rot


def para_svg(d: Diagrama, t: dict) -> str:
    camadas = sorted(d.ordem, key=lambda e: e.z)
    corpo, rotulos = [], []
    for e in camadas:
        if isinstance(e, No):
            corpo.append(svg_no(e, t))
        else:
            linha, rot = svg_aresta(d, e, t)
            corpo.append(linha)
            rotulos.append(rot)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{d.largura}" '
            f'height="{d.altura}" viewBox="0 0 {d.largura} {d.altura}">'
            f'<rect width="100%" height="100%" fill="{t["fundo"]}"/>'
            + "".join(corpo) + "".join(rotulos) + "</svg>")


# =========================================================================== draw.io

def _cor(t, **kw):
    return ";".join(f"{k}={v}" for k, v in kw.items())


def estilo_no(n: No, t: dict) -> str:
    base = (f"html=1;whiteSpace=wrap;fontFamily=Helvetica;fontSize=12;"
            f"fontColor={t['texto']};strokeWidth=1.4;")
    sup, cab, borda, dest = t["superficie"], t["cabecalho"], t["borda"], t["destaque"]
    estilos = {
        "referencia": f"rounded=0;dashed=1;fillColor={sup};strokeColor={t['mudo']};",
        "caso": f"ellipse;fillColor={sup};strokeColor={borda};",
        "ator": f"shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;"
                f"outlineConnect=0;fillColor=none;strokeColor={dest};fontStyle=1;"
                f"strokeWidth=1.6;",
        "estado": f"rounded=1;arcSize=30;fillColor={sup};strokeColor={dest};strokeWidth=1.6;",
        "inicial": f"ellipse;shape=startState;fillColor={t['texto']};strokeColor={t['texto']};",
        "final": f"ellipse;shape=endState;fillColor={t['texto']};strokeColor={t['texto']};",
        "acao": f"rounded=1;arcSize=35;fillColor={sup};strokeColor={borda};",
        "decisao": f"rhombus;fillColor={sup};strokeColor={dest};fontSize=11;strokeWidth=1.6;",
        "fronteira": f"rounded=0;fillColor=none;strokeColor={borda};verticalAlign=top;"
                     f"fontStyle=1;fontColor={t['mudo']};fontSize=13;spacingTop=6;",
        "raia": f"swimlane;startSize=36;fillColor={cab};swimlaneFillColor=none;"
                f"strokeColor={borda};fontStyle=1;fontSize=13;container=0;collapsible=0;",
        "componente": f"rounded=0;fillColor={sup};strokeColor={borda};",
        "externo": f"rounded=0;dashed=1;fillColor={sup};strokeColor={t['mudo']};",
        "container": f"rounded=0;fillColor={t['container']};strokeColor={borda};"
                     f"verticalAlign=top;align=left;spacingLeft=14;spacingTop=6;fontStyle=1;"
                     f"fontColor={t['mudo']};fontSize=13;",
        "banco": f"shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;"
                 f"fillColor={sup};strokeColor={borda};",
        "nota": f"shape=note;size=14;fillColor={t['nota']};strokeColor={t['mudo']};"
                f"fontColor={t['mudo']};align=left;verticalAlign=top;spacingLeft=10;"
                f"spacingTop=4;fontSize=11;strokeWidth=1.1;",
        "cabeca": f"shape=umlLifeline;perimeter=lifelinePerimeter;container=0;"
                  f"collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;"
                  f"size={{size}};fillColor={cab};strokeColor={borda};",
        "ator_seq": f"shape=umlLifeline;participant=umlActor;perimeter=lifelinePerimeter;"
                    f"container=0;collapsible=0;recursiveResize=0;verticalAlign=top;"
                    f"spacingTop=42;outlineConnect=0;portConstraint=eastwest;size=44;"
                    f"fillColor=none;strokeColor={dest};fontStyle=1;",
        "ativacao": f"points=[];perimeter=orthogonalPerimeter;outlineConnect=0;"
                    f"targetShapes=umlLifeline;portConstraint=eastwest;fillColor={cab};"
                    f"strokeColor={borda};strokeWidth=1.2;",
        "fragmento": f"shape=umlFrame;pointerEvents=0;width={{lw}};height=22;"
                     f"fillColor=none;swimlaneFillColor=none;strokeColor={t['mudo']};"
                     f"fontColor={dest};fontStyle=1;strokeWidth=1.2;",
        "guarda": f"text;strokeColor=none;fillColor=none;align=left;verticalAlign=top;"
                  f"fontColor={dest};fontSize=11;labelBackgroundColor={t['fundo']};",
    }
    s = base + estilos[n.tipo]
    if n.tipo == "cabeca":
        s = s.replace("{size}", str(n.h))
    if n.tipo == "fragmento":
        s = s.replace("{lw}", str(int(22 + largura_texto(n.texto[0].texto, 12))))
    return s


def _celula(id, valor, estilo, x, y, w, h, pai="1"):
    return (f'<mxCell id={quoteattr(id)} value={quoteattr(valor)} style={quoteattr(estilo)} '
            f'vertex="1" parent="{pai}"><mxGeometry x="{x:.0f}" y="{y:.0f}" '
            f'width="{w:.0f}" height="{h:.0f}" as="geometry"/></mxCell>')


def drawio_no(n: No, t) -> str:
    if n.tipo == "classe":
        head = CABECALHO_ESTEREOTIPO if n.estereotipo else CABECALHO
        rotulo = escape(n.texto[0].texto)
        if n.estereotipo:
            rotulo = (f'<span style="font-weight:normal;font-size:11px;color:{t["mudo"]}">'
                      f'«{escape(n.estereotipo)}»</span><br>{rotulo}')
        estilo = (f"swimlane;fontStyle=1;align=center;verticalAlign=top;childLayout=stackLayout;"
                  f"horizontal=1;startSize={head};horizontalStack=0;resizeParent=1;"
                  f"resizeParentMax=0;resizeLast=0;collapsible=0;marginBottom=0;html=1;"
                  f"fontFamily=Helvetica;fontSize=13;fillColor={t['cabecalho']};"
                  f"swimlaneFillColor={t['superficie']};strokeColor={t['borda']};"
                  f"fontColor={t['texto']};strokeWidth=1.4;spacingTop=2;")
        s = _celula(n.id, rotulo, estilo, n.x, n.y, n.w, n.h)
        texto_estilo = (f"text;html=1;strokeColor=none;fillColor=none;align=left;"
                        f"verticalAlign=top;spacingLeft=8;spacingTop=2;overflow=hidden;"
                        f"rotatable=0;points=[[0,0.5],[1,0.5]];portConstraint=eastwest;"
                        f"whiteSpace=nowrap;fontFamily=Helvetica;fontSize=12;"
                        f"fontColor={t['texto']};")
        y = head
        if n.atributos:
            ha = len(n.atributos) * ALTURA_LINHA + 10
            s += _celula(f"{n.id}-a", html_linhas(n.atributos, t), texto_estilo, 0, y,
                         n.w, ha, pai=n.id)
            y += ha
        if n.operacoes:
            if n.atributos:
                s += _celula(f"{n.id}-l", "", f"line;strokeWidth=1;fillColor=none;"
                             f"strokeColor={t['borda']};rotatable=0;points=[];"
                             f"portConstraint=eastwest;", 0, y, n.w, 8, pai=n.id)
                y += 8
            ho = len(n.operacoes) * ALTURA_LINHA + 10
            s += _celula(f"{n.id}-o", html_linhas(n.operacoes, t), texto_estilo, 0, y,
                         n.w, ho, pai=n.id)
        return s
    if n.tipo == "ativacao":
        return _celula(n.id, "", estilo_no(n, t), n.x, n.y, n.w, n.h)
    if n.tipo == "cabeca":
        return _celula(n.id, html_linhas(n.texto, t), estilo_no(n, t), n.x, n.y, n.w,
                       n.extra["fim"] - n.y)
    if n.tipo == "ator_seq":
        cx = n.x + n.w / 2
        return _celula(n.id, html_linhas(n.texto, t), estilo_no(n, t), cx - 10, n.y, 20,
                       n.extra["fim"] - n.y)
    if n.tipo == "ator":
        return _celula(n.id, html_linhas(n.texto, t), estilo_no(n, t), n.x + 6, n.y + 6,
                       n.w - 12, n.h - 6)
    if n.tipo == "guarda":
        return _celula(n.id, html_linhas(n.texto, t), estilo_no(n, t), n.x, n.y,
                       largura_texto(n.texto[0].texto, 11.5) + 12, 18)
    if n.tipo == "nota":
        return _celula(n.id, html_linhas(n.texto, t), estilo_no(n, t), n.x, n.y, n.w, n.h)
    return _celula(n.id, html_linhas(n.texto, t), estilo_no(n, t), n.x, n.y, n.w, n.h)


PONTAS_DRAWIO = {
    None: "endArrow=none;",
    "cheia": "endArrow=block;endFill=1;endSize=7;",
    "aberta": "endArrow=open;endFill=0;endSize=9;",
    "vazada": "endArrow=block;endFill=0;endSize=14;",
}


def drawio_aresta(d: Diagrama, a: Aresta, t) -> str:
    tracejada, fim, inicio = TIPOS_ARESTA[a.tipo]
    cor = t["mudo"] if a.tipo == "separador" else t["linha"]
    estilo = (f"edgeStyle=none;rounded=0;html=1;fontFamily=Helvetica;fontSize=11;"
              f"strokeColor={cor};fontColor={t['texto']};strokeWidth=1.3;"
              f"labelBackgroundColor={t['fundo']};" + PONTAS_DRAWIO[fim])
    if inicio == "losango":
        estilo += "startArrow=diamondThin;startFill=1;startSize=14;"
    if tracejada:
        estilo += "dashed=1;dashPattern=6 4;"
    mensagem = a.tipo in ("msg", "retorno")
    valor = ""
    if mensagem:
        estilo += "verticalAlign=bottom;"
        valor = a.rotulos[0].texto if a.rotulos else ""

    if a.p0 is not None:
        geo = (f'<mxGeometry relative="1" as="geometry">'
               f'<mxPoint x="{a.p0[0]:.0f}" y="{a.p0[1]:.0f}" as="sourcePoint"/>'
               f'<mxPoint x="{a.p1[0]:.0f}" y="{a.p1[1]:.0f}" as="targetPoint"/></mxGeometry>')
        ligacao = ""
    else:
        estilo += (f"exitX={a.saida[0]:.4f};exitY={a.saida[1]:.4f};exitDx=0;exitDy=0;"
                   f"exitPerimeter=0;entryX={a.entrada[0]:.4f};entryY={a.entrada[1]:.4f};"
                   f"entryDx=0;entryDy={a.dy_entrada:.0f};entryPerimeter=0;")
        pontos = "".join(f'<mxPoint x="{x:.0f}" y="{y:.0f}"/>' for x, y in a.pontos)
        geo = (f'<mxGeometry relative="1" as="geometry">'
               + (f'<Array as="points">{pontos}</Array>' if pontos else "")
               + "</mxGeometry>")
        ligacao = f' source="{a.origem}" target="{a.destino}"'
    s = (f'<mxCell id="{a.id}" value={quoteattr(escape(valor))} style={quoteattr(estilo)} '
         f'edge="1" parent="1"{ligacao}>{geo}</mxCell>')
    if not mensagem:
        for i, r in enumerate(a.rotulos):
            cor_t = t["destaque"] if r.destaque else t["texto"]
            s += (f'<mxCell id="{a.id}-r{i}" value={quoteattr(escape(r.texto))} '
                  f'style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;'
                  f'points=[];fontFamily=Helvetica;fontSize=11;fontColor={cor_t};'
                  f'labelBackgroundColor={t["fundo"]};" vertex="1" connectable="0" '
                  f'parent="{a.id}"><mxGeometry x="{r.pos:.3f}" relative="1" as="geometry">'
                  f'<mxPoint x="{r.dx:.0f}" y="{r.dy:.0f}" as="offset"/></mxGeometry></mxCell>')
    return s


def para_drawio(diagramas: list[Diagrama], t: dict) -> str:
    paginas = []
    for d in diagramas:
        celulas = []
        for e in sorted(d.ordem, key=lambda e: e.z):
            celulas.append(drawio_no(e, t) if isinstance(e, No) else drawio_aresta(d, e, t))
        paginas.append(
            f'<diagram id="{d.codigo}" name={quoteattr(d.titulo)}>'
            f'<mxGraphModel dx="{d.largura}" dy="{d.altura}" grid="1" gridSize="10" '
            f'guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" '
            f'pageWidth="{d.largura}" pageHeight="{d.altura}" background="{t["fundo"]}" '
            f'math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>'
            + "".join(celulas) + "</root></mxGraphModel></diagram>")
    return ('<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net" '
            'type="device">' + "".join(paginas) + "</mxfile>\n")
