import type { APIRequestContext, Page } from '@playwright/test'

export const SENHA_PADRAO = 'senha-de-e2e-1'

export type Papel = 'PARTICIPANTE' | 'ORGANIZADOR'

export interface UsuarioDeTeste {
  nome: string
  email: string
  senha: string
  token: string
}

/**
 * E-mail único por chamada. É o que permite os testes rodarem em paralelo e repetidas vezes
 * contra o mesmo banco, sem limpar nada entre execuções.
 */
function emailUnico(prefixo: string): string {
  const sufixo = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
  return `${prefixo}-${sufixo}@e2e.unisa.br`
}

/** Cria o usuário pela API e já devolve o token: preparar cenário não é o que se testa. */
export async function criarUsuario(
  api: APIRequestContext,
  papel: Papel,
  prefixo = papel.toLowerCase(),
): Promise<UsuarioDeTeste> {
  const email = emailUnico(prefixo)
  const nome = `${papel === 'ORGANIZADOR' ? 'Org' : 'Part'} ${email.split('-')[1] ?? ''}`.trim()

  const registro = await api.post('/api/auth/registrar', {
    data: { nome, email, senha: SENHA_PADRAO, papel },
  })
  if (!registro.ok()) {
    throw new Error(`Falha ao registrar ${email}: ${registro.status()} ${await registro.text()}`)
  }

  const login = await api.post('/api/auth/login', {
    data: { email, senha: SENHA_PADRAO },
  })
  const corpo = (await login.json()) as { token: string }

  return { nome, email, senha: SENHA_PADRAO, token: corpo.token }
}

/**
 * Data e hora no formato de `<input type="datetime-local">`, deslocada em horas a partir de
 * agora. Negativo produz um instante no passado.
 */
export function dataHoraParaCampo(deslocamentoEmHoras: number): string {
  const quando = new Date(Date.now() + deslocamentoEmHoras * 3_600_000)
  const doisDigitos = (n: number) => String(n).padStart(2, '0')
  return (
    `${quando.getFullYear()}-${doisDigitos(quando.getMonth() + 1)}-${doisDigitos(quando.getDate())}` +
    `T${doisDigitos(quando.getHours())}:${doisDigitos(quando.getMinutes())}`
  )
}

/** ISO local sem fuso, que é o formato que a API usa. */
function isoLocal(deslocamentoEmHoras: number): string {
  return `${dataHoraParaCampo(deslocamentoEmHoras)}:00`
}

export interface OpcoesDeEvento {
  titulo: string
  limiteVagas?: number
  /** Horas a partir de agora. Negativo cria evento que já começou. */
  inicioEmHoras?: number
  fimEmHoras?: number
  publicar?: boolean
}

export async function criarEvento(
  api: APIRequestContext,
  organizador: UsuarioDeTeste,
  opcoes: OpcoesDeEvento,
): Promise<number> {
  const {
    titulo,
    limiteVagas = 10,
    inicioEmHoras = 24,
    fimEmHoras = 27,
    publicar = true,
  } = opcoes

  const resposta = await api.post('/api/eventos', {
    headers: { Authorization: `Bearer ${organizador.token}` },
    data: {
      titulo,
      descricao: `Evento de teste ponta a ponta: ${titulo}.`,
      local: 'Auditório UNISA',
      dataInicio: isoLocal(inicioEmHoras),
      dataFim: isoLocal(fimEmHoras),
      cargaHoraria: 3,
      limiteVagas,
    },
  })
  if (!resposta.ok()) {
    throw new Error(`Falha ao criar evento: ${resposta.status()} ${await resposta.text()}`)
  }
  const { id } = (await resposta.json()) as { id: number }

  if (publicar) {
    const publicacao = await api.patch(`/api/eventos/${id}/status`, {
      headers: { Authorization: `Bearer ${organizador.token}` },
      data: { status: 'PUBLICADO' },
    })
    if (!publicacao.ok()) {
      throw new Error(
        `Falha ao publicar o evento ${id}: ${publicacao.status()} ${await publicacao.text()}`,
      )
    }
  }
  return id
}

export async function inscrever(
  api: APIRequestContext,
  participante: UsuarioDeTeste,
  eventoId: number,
): Promise<{ id: number; status: string; posicaoFila?: number }> {
  const resposta = await api.post(`/api/eventos/${eventoId}/inscricoes`, {
    headers: { Authorization: `Bearer ${participante.token}` },
  })
  if (!resposta.ok()) {
    throw new Error(`Falha ao inscrever: ${resposta.status()} ${await resposta.text()}`)
  }
  return (await resposta.json()) as { id: number; status: string; posicaoFila?: number }
}

/**
 * Entra pela interface. O login é parte do que se testa, então não há atalho por token aqui:
 * a sessão nasce do mesmo caminho que a do usuário real.
 */
export async function entrarNaInterface(page: Page, usuario: UsuarioDeTeste): Promise<void> {
  await page.goto('/entrar')
  await page.getByLabel('E-mail').fill(usuario.email)
  await page.getByLabel('Senha').fill(usuario.senha)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await page.getByRole('button', { name: 'Sair' }).waitFor()
}
