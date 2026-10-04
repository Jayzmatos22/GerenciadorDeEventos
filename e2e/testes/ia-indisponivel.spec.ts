import { expect, test } from '@playwright/test'
import { criarUsuario, dataHoraParaCampo, entrarNaInterface, marcadorUnico } from '../apoio/dados'

/**
 * RN-10: falha na IA nunca bloqueia o fluxo. A pilha do E2E sobe com `IA_HABILITADA=false`,
 * então este é o cenário real de quem roda o sistema sem modelo no ar — e é o único lado da
 * IA verificável sem um Ollama ou uma chave de API.
 */
test('IA indisponível avisa e o cadastro manual segue até o fim - RN10', async ({
  page,
  request,
}) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const titulo = `Palestra sem IA ${marcadorUnico()}`

  await entrarNaInterface(page, organizador)
  await page.goto('/organizador/eventos/novo')

  await page
    .getByLabel('Texto do evento')
    .fill('Palestra sobre Docker no auditório, dia 20 de novembro de 2026, das 19h às 22h.')
  await page.getByRole('button', { name: 'Interpretar texto' }).click()

  await expect(page.getByText('Assistente de IA indisponível')).toBeVisible()
  await expect(
    page.getByText('Siga preenchendo o formulário abaixo normalmente', { exact: false }),
  ).toBeVisible()

  // O ponto da regra: o formulário manual continua inteiro e utilizável.
  await page.getByLabel('Título').fill(titulo)
  await page.getByLabel('Descrição').fill('Cadastrada à mão porque a IA estava fora do ar.')
  await page.getByLabel('Local').fill('Auditório UNISA')
  await page.getByLabel('Início').fill(dataHoraParaCampo(48))
  await page.getByLabel('Fim').fill(dataHoraParaCampo(51))
  await page.getByLabel('Carga horária (horas)').fill('3')
  await page.getByLabel('Limite de vagas').fill('40')
  await page.getByRole('button', { name: 'Criar rascunho' }).click()

  await expect(page.getByRole('heading', { name: titulo })).toBeVisible()
  await expect(page.getByText('RASCUNHO', { exact: true })).toBeVisible()
})

test('a resposta de IA indisponível não expõe stacktrace ao usuário', async ({ request }) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')

  const resposta = await request.post('/api/eventos/interpretar', {
    headers: { Authorization: `Bearer ${organizador.token}` },
    data: { texto: 'Qualquer texto de evento.' },
  })

  expect(resposta.status()).toBe(503)
  const corpo = await resposta.text()
  expect(corpo).toContain('IA_INDISPONIVEL')
  expect(corpo).not.toContain('Exception')
  expect(corpo).not.toContain('br.unisa.eventos')
})
