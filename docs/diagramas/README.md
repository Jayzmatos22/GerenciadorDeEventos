# Diagramas do Apêndice A

Onze diagramas UML em três temas escuros, conferidos contra o código.

| Pasta | Tema |
| --- | --- |
| `v1-grafite/` | cinza grafite, quase monocromático; azul só em ator, estado e guarda |
| `v2-azul-noite/` | azul-marinho com contorno azul |
| `v3-contraste/` | preto, contorno branco e âmbar como única cor |

Cada pasta tem um `.png` por diagrama (para colar no documento), o `.svg` correspondente e um
`.drawio` com os onze diagramas em páginas separadas — abra em <https://app.diagrams.net> para
editar. Os `diagramas-*.pdf` na raiz desta pasta são a prévia de cada tema, um diagrama por página.

## O que mudou em relação aos diagramas anteriores

- **Classes:** só operações que existem no código. As anteriores (`publicar`, `encerrar`,
  `vagasDisponiveis`, `estaLotado`, `confirmar`, `enfileirar`…) não existiam.
- **Estados da inscrição:** saiu a transição para `AUSENTE`. O valor existe no enum, mas
  nenhum fluxo da versão atual o grava.
- **Certificado:** virou duas sequências. O PDF não é gerado na emissão, e sim no download,
  em outra requisição (`CertificadoService.gerarPdf`).
- **Divididos para ficarem legíveis:** casos de uso, classes e estados ganharam dois diagramas.

## Regerar

Da raiz do repositório:

```bash
python3 docs/diagramas/fonte/gerar.py
NODE_PATH=e2e/node_modules node docs/diagramas/fonte/renderizar.mjs
```

O conteúdo de cada diagrama está em `fonte/diagramas.py`; as cores, em `fonte/gerar.py`.
