"""Gera os diagramas do Apendice A nos tres temas escuros.

Uso, a partir da raiz do repositorio:
    python3 docs/diagramas/fonte/gerar.py
    node docs/diagramas/fonte/renderizar.mjs      # PNG e PDF a partir dos SVG

Saida em docs/diagramas/<tema>/: um .drawio com uma pagina por diagrama (editavel no
draw.io) e um .svg por diagrama. O renderizador acrescenta os .png e um .pdf de previa.
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))

from diagramas import ARQUIVOS, TODOS  # noqa: E402
from saidas import para_drawio, para_svg  # noqa: E402

TEMAS = {
    # Cinza grafite, quase monocromatico; o azul so marca ator, estado e guarda.
    "v1-grafite": dict(nome="Grafite", fundo="#17181b", superficie="#222429",
                       cabecalho="#2e3138", borda="#8b939e", texto="#eceff4",
                       mudo="#9aa3ae", destaque="#7aa2f7", linha="#b4bcc8",
                       nota="#1d1f23", container="#1b1d21"),
    # Azul-marinho com contorno azul: o mais "tecnico" dos tres.
    "v2-azul-noite": dict(nome="Azul-noite", fundo="#0b1120", superficie="#111a2e",
                          cabecalho="#18264a", borda="#4f7fc4", texto="#e6edf7",
                          mudo="#8ea3c2", destaque="#38bdf8", linha="#9db6da",
                          nota="#0e1628", container="#0d1526"),
    # Preto com contorno branco e ambar como unica cor: o de maior contraste.
    "v3-contraste": dict(nome="Contraste", fundo="#0a0a0a", superficie="#141414",
                         cabecalho="#222222", borda="#e5e5e5", texto="#fafafa",
                         mudo="#a3a3a3", destaque="#f5a524", linha="#d4d4d4",
                         nota="#121212", container="#0f0f0f"),
}

RAIZ = pathlib.Path(__file__).resolve().parent.parent


def main():
    diagramas = [f() for f in TODOS]
    indice = []
    for chave, tema in TEMAS.items():
        pasta = RAIZ / chave
        pasta.mkdir(parents=True, exist_ok=True)
        (pasta / f"diagramas-{chave}.drawio").write_text(para_drawio(diagramas, tema),
                                                          encoding="utf-8")
        itens = []
        for d in diagramas:
            nome = ARQUIVOS[d.codigo]
            (pasta / f"{nome}.svg").write_text(para_svg(d, tema), encoding="utf-8")
            itens.append({"arquivo": nome, "titulo": d.titulo,
                          "largura": d.largura, "altura": d.altura})
        indice.append({"tema": chave, "nome": tema["nome"], "fundo": tema["fundo"],
                       "mudo": tema["mudo"], "itens": itens})
        print(f"{chave}: {len(diagramas)} diagramas")
    (RAIZ / "fonte" / "indice.json").write_text(json.dumps(indice, ensure_ascii=False,
                                                           indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
