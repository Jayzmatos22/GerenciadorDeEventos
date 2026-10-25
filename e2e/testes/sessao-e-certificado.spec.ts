import { expect, test } from '@playwright/test'
import {
  criarEvento,
  criarUsuario,
  entrarNaInterface,
  inscrever,
  marcadorUnico,
  registrarPresenca,
} from '../apoio/dados'

/**
 * RN-05: o certificado depende só da presença. A tela esperava também o fim do evento, e o
 * participante que fazia check-in no meio da atividade não via o botão.
 */
test('certificado aparece logo depois do check-in, com o evento ainda acontecendo - RN05', async ({
  page,
  request,
}) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const participante = await criarUsuario(request, 'PARTICIPANTE')
  const eventoId = await criarEvento(request, organizador, {
    titulo: `Palestra em andamento ${marcadorUnico()}`,
    inicioEmHoras: -1,
    fimEmHoras: 3,
  })
  const inscricao = await inscrever(request, participante, eventoId)

  await entrarNaInterface(page, participante)
  await page.getByRole('link', { name: /Minhas inscrições/ }).click()
  await expect(
    page.getByText('O certificado é liberado assim que o organizador registrar sua presença.'),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Baixar certificado' })).toHaveCount(0)

  await registrarPresenca(request, organizador, eventoId, inscricao.id)
  await page.reload()

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar certificado' }).click()
  expect((await download).suggestedFilename()).toMatch(/^certificado-\d+\.pdf$/)
  // RN-06: avaliar continua esperando o fim do evento.
  await expect(page.getByRole('button', { name: 'Avaliar evento' })).toHaveCount(0)
})

/**
 * Cada aba guarda a própria sessão. Com a sessão no localStorage, entrar como participante
 * numa aba trocava o token da aba do organizador, que passava a ver "esta área não é do seu
 * perfil" e a receber 403.
 */
test('organizador e participante em abas diferentes do mesmo navegador', async ({
  page,
  context,
  request,
}) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const participante = await criarUsuario(request, 'PARTICIPANTE')
  const titulo = `Evento de duas abas ${marcadorUnico()}`
  await criarEvento(request, organizador, { titulo })

  await entrarNaInterface(page, organizador)
  await page.goto('/organizador')
  await expect(page.getByRole('link', { name: titulo })).toBeVisible()

  const abaDoParticipante = await context.newPage()
  await entrarNaInterface(abaDoParticipante, participante)
  await expect(abaDoParticipante.getByRole('link', { name: /Minhas inscrições/ })).toBeVisible()

  // A aba do organizador continua sendo do organizador, mesmo recarregada.
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Meus eventos' })).toBeVisible()
  await expect(page.getByRole('link', { name: titulo })).toBeVisible()
  await expect(page.getByText('Esta área não é do seu perfil')).toHaveCount(0)

  await abaDoParticipante.reload()
  await expect(abaDoParticipante.getByRole('link', { name: /Minhas inscrições/ })).toBeVisible()
  await expect(abaDoParticipante.getByRole('link', { name: /Meus eventos/ })).toHaveCount(0)
})
