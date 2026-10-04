import { expect, test } from '@playwright/test'
import { criarUsuario, dataHoraParaCampo, entrarNaInterface } from '../apoio/dados'

/**
 * O caminho que a seção 11 da especificação nomeia como E2E:
 * criar evento → inscrever → check-in → certificado.
 *
 * Tudo pela interface, de ponta a ponta. A API só é usada para criar as contas, que são
 * pré-condição e não o que está sendo verificado aqui.
 */
test('criar evento, inscrever, registrar presença e emitir certificado', async ({
  page,
  request,
}) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const participante = await criarUsuario(request, 'PARTICIPANTE')
  const titulo = `Oficina ponta a ponta ${Date.now().toString(36)}`

  // ---------------------------------------------------------------- criar e publicar
  await entrarNaInterface(page, organizador)
  await page.getByRole('link', { name: /Meus eventos/ }).click()
  await page.getByRole('link', { name: 'Novo evento' }).click()

  await page.getByLabel('Título').fill(titulo)
  await page.getByLabel('Descrição').fill('Oficina usada no teste de ponta a ponta.')
  await page.getByLabel('Local').fill('Laboratório 3')
  // Evento que já começou e já terminou: a janela de check-in da RN-04 continua aberta pela
  // tolerância, e o certificado só aparece na interface depois que o evento acaba.
  await page.getByLabel('Início').fill(dataHoraParaCampo(-6))
  await page.getByLabel('Fim').fill(dataHoraParaCampo(-2))
  await page.getByLabel('Carga horária (horas)').fill('4')
  await page.getByLabel('Limite de vagas').fill('5')
  await page.getByRole('button', { name: 'Criar rascunho' }).click()

  await expect(page.getByRole('heading', { name: titulo })).toBeVisible()
  await expect(page.getByText('RASCUNHO', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Publicar' }).click()
  // Exato: sem isso o seletor também casaria com o aviso "Evento agora está publicado.".
  await expect(page.getByText('PUBLICADO', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Sair' }).click()

  // ---------------------------------------------------------------- inscrever
  await entrarNaInterface(page, participante)
  await page.getByRole('link', { name: 'Eventos', exact: true }).click()
  await page.getByLabel('Buscar').fill(titulo)
  await page.getByRole('button', { name: 'Filtrar' }).click()
  await expect(page.getByText('Atualizando a lista…')).toHaveCount(0)

  await page.getByRole('heading', { name: titulo }).click()
  await expect(page.getByText('5 de 5 disponíveis')).toBeVisible()

  await page.getByRole('button', { name: 'Quero me inscrever' }).click()
  await expect(page.getByText('Inscrição confirmada')).toBeVisible()
  await expect(page.getByText('4 de 5 disponíveis')).toBeVisible()

  await page.getByRole('button', { name: 'Sair' }).click()

  // ---------------------------------------------------------------- check-in
  await entrarNaInterface(page, organizador)
  await page.getByRole('link', { name: /Meus eventos/ }).click()
  await page.getByRole('link', { name: titulo }).click()

  const linhaDoParticipante = page.getByRole('listitem').filter({
    hasText: participante.email,
  })
  await expect(linhaDoParticipante).toBeVisible()

  await linhaDoParticipante.getByRole('button', { name: 'Registrar presença' }).click()
  await expect(page.getByText('Presença registrada.')).toBeVisible()
  await expect(linhaDoParticipante.getByText('Presença registrada')).toBeVisible()

  await page.getByRole('button', { name: 'Sair' }).click()

  // ---------------------------------------------------------------- certificado
  await entrarNaInterface(page, participante)
  await page.getByRole('link', { name: /Minhas inscrições/ }).click()

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar certificado' }).click()
  const arquivo = await download

  expect(arquivo.suggestedFilename()).toMatch(/^certificado-\d+\.pdf$/)
  const caminho = await arquivo.path()
  const { readFileSync } = await import('node:fs')
  expect(readFileSync(caminho).subarray(0, 5).toString()).toBe('%PDF-')

  // ---------------------------------------------------------------- validação pública
  const codigo = await page.getByText(/^[0-9A-F]{32}$/).innerText()
  expect(codigo).toHaveLength(32)

  await page.getByRole('link', { name: 'Conferir a página pública de validação' }).click()
  await expect(page.getByText('Certificado autêntico, emitido por este sistema.')).toBeVisible()
  await expect(page.getByText(organizador.nome, { exact: false })).toHaveCount(0)
  await expect(page.getByText(titulo)).toBeVisible()
  await expect(page.getByText('4 hora(s)')).toBeVisible()
})

test('a validação de certificado é pública e não exige sessão', async ({ page, request }) => {
  const organizador = await criarUsuario(request, 'ORGANIZADOR')
  const participante = await criarUsuario(request, 'PARTICIPANTE')

  const { criarEvento, inscrever } = await import('../apoio/dados')
  const eventoId = await criarEvento(request, organizador, {
    titulo: `Evento validação ${Date.now().toString(36)}`,
    inicioEmHoras: -6,
    fimEmHoras: -2,
  })
  const inscricao = await inscrever(request, participante, eventoId)

  await request.post(`/api/eventos/${eventoId}/presencas`, {
    headers: { Authorization: `Bearer ${organizador.token}` },
    data: { inscricaoId: inscricao.id },
  })
  const emissao = await request.post(`/api/inscricoes/${inscricao.id}/certificado`, {
    headers: { Authorization: `Bearer ${participante.token}` },
  })
  const { codigoAutenticidade } = (await emissao.json()) as { codigoAutenticidade: string }

  // Sem passar por /entrar em momento algum.
  await page.goto(`/certificados/validar/${codigoAutenticidade}`)
  await expect(page.getByText('Certificado autêntico, emitido por este sistema.')).toBeVisible()
  await expect(page.getByText(participante.nome)).toBeVisible()
})

test('código inexistente não confirma nada', async ({ page }) => {
  await page.goto('/certificados/validar/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')
  await expect(page.getByText('Certificado não encontrado')).toBeVisible()
  await expect(page.getByText('Certificado autêntico, emitido por este sistema.')).toHaveCount(0)
})
