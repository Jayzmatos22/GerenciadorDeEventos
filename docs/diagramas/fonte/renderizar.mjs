// Gera PNG (2x) de cada SVG e um PDF de previa por tema, com o Chromium do Playwright.
//
// Uso, da raiz do repositorio, depois do gerar.py:
//   NODE_PATH=e2e/node_modules node docs/diagramas/fonte/renderizar.mjs
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const { chromium } = require('@playwright/test')

const fonte = dirname(fileURLToPath(import.meta.url))
const raiz = dirname(fonte)
const indice = JSON.parse(readFileSync(join(fonte, 'indice.json'), 'utf8'))

const navegador = await chromium.launch()
const pagina = await navegador.newPage({ deviceScaleFactor: 2 })

for (const tema of indice) {
  const pasta = join(raiz, tema.tema)

  for (const item of tema.itens) {
    const svg = readFileSync(join(pasta, `${item.arquivo}.svg`), 'utf8')
    await pagina.setViewportSize({ width: item.largura, height: item.altura })
    await pagina.setContent(`<html><body style="margin:0">${svg}</body></html>`)
    await pagina.locator('svg').screenshot({ path: join(pasta, `${item.arquivo}.png`) })
  }

  // PDF de previa: uma pagina A4 paisagem por diagrama, com o titulo discreto no topo.
  const paginas = tema.itens.map((item) => {
    const svg = readFileSync(join(pasta, `${item.arquivo}.svg`), 'utf8')
      .replace('<svg ', '<svg preserveAspectRatio="xMidYMid meet" ')
    return `<section><h1>${item.titulo}</h1><div class="quadro">${svg}</div></section>`
  }).join('')
  await pagina.setContent(`<html><head><style>
    @page { size: A4 landscape; margin: 0 }
    html, body { margin: 0; background: ${tema.fundo} }
    section { width: 297mm; height: 210mm; box-sizing: border-box; padding: 9mm 11mm;
              page-break-after: always; display: flex; flex-direction: column }
    h1 { font: 600 11pt Helvetica, Arial, sans-serif; color: ${tema.mudo}; margin: 0 0 4mm }
    .quadro { flex: 1; min-height: 0; display: flex; align-items: center;
              justify-content: center }
    .quadro svg { max-width: 100%; max-height: 100%; width: auto; height: auto }
  </style></head><body>${paginas}</body></html>`)
  await pagina.pdf({ path: join(raiz, `diagramas-${tema.tema}.pdf`), format: 'A4',
    landscape: true, printBackground: true })
  console.log(`${tema.tema}: ${tema.itens.length} PNG + PDF`)
}

await navegador.close()
