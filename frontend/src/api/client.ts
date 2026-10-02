import type {
  CampoInvalido,
  CodigoErro,
  ErroResponse,
  UsuarioResponse,
} from '../types/api'

const BASE = import.meta.env.VITE_API_URL ?? ''

const CHAVE_TOKEN = 'eventos.token'
const CHAVE_USUARIO = 'eventos.usuario'

/**
 * Erro vindo do envelope único da API (seção 6 da especificação técnica). Guarda o `codigo`
 * para a tela decidir o que fazer: IA_INDISPONIVEL abre o formulário manual, VALIDACAO
 * destaca os campos, e assim por diante.
 */
export class ApiError extends Error {
  readonly status: number
  readonly codigo: CodigoErro
  readonly campos: CampoInvalido[]

  constructor(envelope: Partial<ErroResponse> & { status: number }) {
    super(envelope.mensagem ?? 'Não foi possível concluir a operação.')
    this.name = 'ApiError'
    this.status = envelope.status
    this.codigo = envelope.codigo ?? 'ERRO_INTERNO'
    this.campos = envelope.campos ?? []
  }

  /** Mensagens por campo, no formato que os formulários consomem. */
  get errosPorCampo(): Record<string, string> {
    return Object.fromEntries(this.campos.map(({ campo, erro }) => [campo, erro]))
  }
}

export const sessao = {
  token: (): string | null => localStorage.getItem(CHAVE_TOKEN),

  usuario: (): UsuarioResponse | null => {
    const bruto = localStorage.getItem(CHAVE_USUARIO)
    if (!bruto) return null
    try {
      return JSON.parse(bruto) as UsuarioResponse
    } catch {
      // Sessão corrompida no navegador não deve derrubar a aplicação inteira.
      localStorage.removeItem(CHAVE_USUARIO)
      return null
    }
  },

  salvar: (token: string, usuario: UsuarioResponse): void => {
    localStorage.setItem(CHAVE_TOKEN, token)
    localStorage.setItem(CHAVE_USUARIO, JSON.stringify(usuario))
  },

  limpar: (): void => {
    localStorage.removeItem(CHAVE_TOKEN)
    localStorage.removeItem(CHAVE_USUARIO)
  },
}

function cabecalhos(corpoEhJson: boolean): HeadersInit {
  const cabecalho: Record<string, string> = {}
  const token = sessao.token()
  if (token) cabecalho.Authorization = `Bearer ${token}`
  if (corpoEhJson) cabecalho['Content-Type'] = 'application/json'
  return cabecalho
}

async function tratarResposta(resposta: Response): Promise<unknown> {
  if (resposta.status === 204) return null

  const tipo = resposta.headers.get('content-type') ?? ''

  if (!resposta.ok) {
    let envelope: Partial<ErroResponse> & { status: number } = { status: resposta.status }
    if (tipo.includes('application/json')) {
      try {
        const corpo = (await resposta.json()) as ErroResponse
        envelope = { ...corpo, status: resposta.status }
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

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

interface OpcoesRequisicao {
  corpo?: unknown
  formData?: FormData
}

/**
 * O retorno é tipado pelo chamador. O `as T` é a única asserção da camada: a resposta vem de
 * fora e o TypeScript não pode verificá-la em tempo de compilação — é o contrato de
 * `types/api.ts` que garante o acerto, e ele é derivado dos DTOs do backend.
 */
async function requisicao<T>(
  metodo: Metodo,
  caminho: string,
  { corpo, formData }: OpcoesRequisicao = {},
): Promise<T> {
  const corpoDaRequisicao = formData ?? (corpo !== undefined ? JSON.stringify(corpo) : null)

  const resposta = await fetch(`${BASE}${caminho}`, {
    method: metodo,
    headers: cabecalhos(corpo !== undefined),
    body: corpoDaRequisicao,
  })
  return (await tratarResposta(resposta)) as T
}

export const api = {
  get: <T>(caminho: string): Promise<T> => requisicao<T>('GET', caminho),

  /** Baixa um PDF; o backend responde `application/pdf` nas rotas de certificado. */
  getPdf: (caminho: string): Promise<Blob> => requisicao<Blob>('GET', caminho),

  post: <T>(caminho: string, corpo?: unknown): Promise<T> =>
    requisicao<T>('POST', caminho, { corpo }),

  put: <T>(caminho: string, corpo: unknown): Promise<T> =>
    requisicao<T>('PUT', caminho, { corpo }),

  patch: <T>(caminho: string, corpo: unknown): Promise<T> =>
    requisicao<T>('PATCH', caminho, { corpo }),

  delete: (caminho: string): Promise<null> => requisicao<null>('DELETE', caminho),

  upload: <T>(caminho: string, formData: FormData): Promise<T> =>
    requisicao<T>('POST', caminho, { formData }),
}

type ValorDeFiltro = string | number | undefined | null

/** Monta a query string ignorando filtros vazios. */
export function query(parametros: Record<string, ValorDeFiltro>): string {
  const busca = new URLSearchParams()
  Object.entries(parametros).forEach(([chave, valor]) => {
    if (valor !== undefined && valor !== null && valor !== '') busca.set(chave, String(valor))
  })
  const texto = busca.toString()
  return texto ? `?${texto}` : ''
}
