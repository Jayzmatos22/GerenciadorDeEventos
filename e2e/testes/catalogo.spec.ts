import { expect, test } from '@playwright/test'
import { criarEvento, criarUsuario } from '../apoio/dados'

/**
 * Regressão: a resposta de uma busca antiga não pode repor a lista velha na tela.
 *
 * O catálogo mantém o resultado anterior visível enquanto a próxima busca não chega, para não
 * piscar vazio. Isso abriu espaço para uma corrida: se a primeira resposta demorasse mais que
 * a segunda, ela chegava depois e desfazia o filtro. O conserto foi cancelar o pedido anterior
 * com AbortController; este teste força exatamente essa ordem invertida.
 */
test('resposta antiga não desfaz o filtro já aplicado', async ({ page, request }) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const marcador = Date.now().toString(36)
  const titulo = `Busca com corrida ${marcador}`

  await criarEvento(request, organizador, { titulo })
  await criarEvento(request, organizador, { titulo: `Outro evento qualquer ${marcador}` })

  // Só a primeira chamada ao catálogo é atrasada, de modo que ela responda depois da busca
  // filtrada que vem em seguida.
  let primeiraChamada = true
  await page.route('**/api/eventos?**', async (rota) => {
    if (primeiraChamada) {
      primeiraChamada = false
      await new Promise((resolver) => setTimeout(resolver, 2_500))
    }
    await rota.continue()
  })

  await page.goto('/')
  await page.getByLabel('Buscar').fill(titulo)
  await page.getByRole('button', { name: 'Filtrar' }).click()

  await expect(page.getByText('Atualizando a lista…')).toHaveCount(0)
  await expect(page.getByRole('heading', { level: 2, name: titulo })).toBeVisible()

  // Dá tempo de a resposta atrasada chegar; se ela vencesse, o filtro se desfaria aqui.
  await page.waitForTimeout(3_000)

  await expect(page.getByRole('heading', { level: 2 })).toHaveCount(1)
  await expect(page.getByRole('heading', { level: 2, name: titulo })).toBeVisible()
})

test('o catálogo mostra apenas eventos publicados', async ({ page, request }) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const marcador = Date.now().toString(36)

  await criarEvento(request, organizador, { titulo: `Publicado visível ${marcador}` })
  await criarEvento(request, organizador, {
    titulo: `Rascunho oculto ${marcador}`,
    publicar: false,
  })

  await page.goto('/')
  await page.getByLabel('Buscar').fill(marcador)
  await page.getByRole('button', { name: 'Filtrar' }).click()
  await expect(page.getByText('Atualizando a lista…')).toHaveCount(0)

  await expect(page.getByRole('heading', { level: 2, name: `Publicado visível ${marcador}` }))
    .toBeVisible()
  await expect(page.getByRole('heading', { level: 2 })).toHaveCount(1)
})
