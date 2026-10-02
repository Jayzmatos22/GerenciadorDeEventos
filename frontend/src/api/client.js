const BASE = import.meta.env.VITE_API_URL ?? ''

const CHAVE_TOKEN = 'eventos.token'
const CHAVE_USUARIO = 'eventos.usuario'

/**
 * Erro vindo do envelope único da API (seção 6 da especificação técnica). Guarda o `codigo`
 * para a tela decidir o que fazer: IA_INDISPONIVEL abre o formulário manual, VALIDACAO
 * destaca os campos, e assim por diante.
 */
export class ApiError extends Error {
  constructor({ status, codigo, mensagem, campos }) {
    super(mensagem ?? 'Não foi possível concluir a operação.')
    this.name = 'ApiError'
    this.status = status
    this.codigo = codigo ?? 'ERRO_INTERNO'
    this.campos = campos ?? []
  }

  /** Mensagens por campo, no formato que os formulários consomem. */
  get errosPorCampo() {
    return Object.fromEntries(this.campos.map(({ campo, erro }) => [campo, erro]))
  }
}

export const sessao = {
  token: () => localStorage.getItem(CHAVE_TOKEN),
  usuario: () => {
    const bruto = localStorage.getItem(CHAVE_USUARIO)
    return bruto ? JSON.parse(bruto) : null
  },
  salvar: (token, usuario) => {
    localStorage.setItem(CHAVE_TOKEN, token)
    localStorage.setItem(CHAVE_USUARIO, JSON.stringify(usuario))
  },
  limpar: () => {
    localStorage.removeItem(CHAVE_TOKEN)
    localStorage.removeItem(CHAVE_USUARIO)
  },
}

function cabecalhos(corpoEhJson) {
  const cabecalho = {}
  const token = sessao.token()
  if (token) cabecalho.Authorization = `Bearer ${token}`
  if (corpoEhJson) cabecalho['Content-Type'] = 'application/json'
  return cabecalho
}

async function tratarResposta(resposta) {
  if (resposta.status === 204) return null

  const tipo = resposta.headers.get('content-type') ?? ''

  if (!resposta.ok) {
    let envelope = { status: resposta.status }
    if (tipo.includes('application/json')) {
      try {
        envelope = { ...(await resposta.json()), status: resposta.status }
      } catch {
        // Resposta de erro sem corpo JSON: fica só com o status.
      }
    }
    throw new ApiError(envelope)
  }

  if (tipo.includes('application/pdf')) return resposta.blob()
  if (tipo.includes('application/json')) return resposta.json()
  return resposta.text()
}

async function requisicao(metodo, caminho, { corpo, formData } = {}) {
  const resposta = await fetch(`${BASE}${caminho}`, {
    method: metodo,
    headers: cabecalhos(Boolean(corpo)),
    body: formData ?? (corpo ? JSON.stringify(corpo) : undefined),
  })
  return tratarResposta(resposta)
}

export const api = {
  get: (caminho) => requisicao('GET', caminho),
  post: (caminho, corpo) => requisicao('POST', caminho, { corpo }),
  put: (caminho, corpo) => requisicao('PUT', caminho, { corpo }),
  patch: (caminho, corpo) => requisicao('PATCH', caminho, { corpo }),
  delete: (caminho) => requisicao('DELETE', caminho),
  upload: (caminho, formData) => requisicao('POST', caminho, { formData }),
}

/** Monta a query string ignorando filtros vazios. */
export function query(parametros) {
  const busca = new URLSearchParams()
  Object.entries(parametros).forEach(([chave, valor]) => {
    if (valor !== undefined && valor !== null && valor !== '') busca.set(chave, valor)
  })
  const texto = busca.toString()
  return texto ? `?${texto}` : ''
}
