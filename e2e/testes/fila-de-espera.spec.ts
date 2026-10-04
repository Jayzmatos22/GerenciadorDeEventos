import { expect, test } from '@playwright/test'
import { criarEvento, criarUsuario, entrarNaInterface, inscrever } from '../apoio/dados'

/**
 * RN-01 e RN-02 do ponto de vista de quem usa o sistema. A disputa concorrente pela última
 * vaga é coberta no teste de integração, com duas threads de verdade; aqui o que importa é
 * que a pessoa entenda onde ficou na fila e veja a vaga chegar quando alguém desiste.
 */
test('evento lotado coloca o participante na fila e o cancelamento promove - RN01 e RN02', async ({
  page,
  request,
}) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const primeiro = await criarUsuario(request, 'PARTICIPANTE', 'confirmado')
  const segundo = await criarUsuario(request, 'PARTICIPANTE', 'fila')

  const titulo = `Vaga única ${Date.now().toString(36)}`
  const eventoId = await criarEvento(request, organizador, { titulo, limiteVagas: 1 })

  const inscricaoDoPrimeiro = await inscrever(request, primeiro, eventoId)
  expect(inscricaoDoPrimeiro.status).toBe('CONFIRMADA')

  // ---------------------------------------------------------------- entra na fila pela interface
  await entrarNaInterface(page, segundo)
  await page.goto(`/eventos/${eventoId}`)

  await expect(page.getByText('Sem vagas', { exact: false })).toHaveCount(0)
  await expect(page.getByText('0 de 1 disponíveis')).toBeVisible()

  await page.getByRole('button', { name: 'Entrar na fila de espera' }).click()
  await expect(page.getByText('O evento está lotado', { exact: false })).toBeVisible()
  await expect(page.getByText('posição 1', { exact: false })).toBeVisible()

  await page.getByRole('link', { name: /Minhas inscrições/ }).click()
  await expect(page.getByText('EM ESPERA', { exact: true })).toBeVisible()
  await expect(page.getByText('1º da fila')).toBeVisible()

  // ---------------------------------------------------------------- a vaga é liberada
  const cancelamento = await request.delete(`/api/inscricoes/${inscricaoDoPrimeiro.id}`, {
    headers: { Authorization: `Bearer ${primeiro.token}` },
  })
  expect(cancelamento.status()).toBe(204)

  await page.reload()
  await expect(page.getByText('CONFIRMADA', { exact: true })).toBeVisible()
  await expect(page.getByText('1º da fila')).toHaveCount(0)
})

test('o catálogo avisa que a inscrição vai para a fila antes de a pessoa clicar', async ({
  page,
  request,
}) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const ocupante = await criarUsuario(request, 'PARTICIPANTE', 'ocupante')

  const titulo = `Lotado no catálogo ${Date.now().toString(36)}`
  const eventoId = await criarEvento(request, organizador, { titulo, limiteVagas: 1 })
  await inscrever(request, ocupante, eventoId)

  await page.goto('/')
  await page.getByLabel('Buscar').fill(titulo)
  await page.getByRole('button', { name: 'Filtrar' }).click()
  await expect(page.getByText('Atualizando a lista…')).toHaveCount(0)

  // Escopado ao cartão do evento: o catálogo guarda a lista anterior enquanto a nova busca
  // não chega, então assertar na página inteira pegaria resultados de outro teste.
  const cartao = page.getByRole('link').filter({ hasText: titulo })
  await expect(cartao.getByText('Sem vagas — inscrição entra na fila de espera')).toBeVisible()
})
