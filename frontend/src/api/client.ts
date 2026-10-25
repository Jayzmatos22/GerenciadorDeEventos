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

/**
 * Cada aba guarda a própria sessão no `sessionStorage`.
 *
 * O `localStorage` é um só para todas as abas do navegador: entrar como participante numa aba
 * trocava, por baixo, o token da aba aberta como organizador, que seguia com o menu de
 * organizador e passava a receber 403 em tudo. O `localStorage` fica só com o último login,
 * para que uma aba nova, ou o site reaberto, já comece autenticada.
 */
function herdarUltimoLogin(): void {
  if (sessionStorage.getItem(CHAVE_TOKEN) !== null) return
  const token = localStorage.getItem(CHAVE_TOKEN)
  const usuario = localStorage.getItem(CHAVE_USUARIO)
  if (token && usuario) {
    sessionStorage.setItem(CHAVE_TOKEN, token)
    sessionStorage.setItem(CHAVE_USUARIO, usuario)
  }
}

herdarUltimoLogin()

export const sessao = {
  token: (): string | null => sessionStorage.getItem(CHAVE_TOKEN),

  usuario: (): UsuarioResponse | null => {
    const bruto = sessionStorage.getItem(CHAVE_USUARIO)
    if (!bruto) return null
    try {
      return JSON.parse(bruto) as UsuarioResponse
    } catch {
      // Sessão corrompida no navegador não deve derrubar a aplicação inteira.
      sessionStorage.removeItem(CHAVE_USUARIO)
      return null
    }
  },

  salvar: (token: string, usuario: UsuarioResponse): void => {
    for (const armazenamento of [sessionStorage, localStorage]) {
      armazenamento.setItem(CHAVE_TOKEN, token)
      armazenamento.setItem(CHAVE_USUARIO, JSON.stringify(usuario))
    }
  },

  limpar: (): void => {
    // O último login só é esquecido se for o desta aba; o de outra aba continua valendo.
    if (localStorage.getItem(CHAVE_TOKEN) === sessionStorage.getItem(CHAVE_TOKEN)) {
      localStorage.removeItem(CHAVE_TOKEN)
      localStorage.removeItem(CHAVE_USUARIO)
    }
    sessionStorage.removeItem(CHAVE_TOKEN)
    sessionStorage.removeItem(CHAVE_USUARIO)
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
  /**
   * Permite abortar o pedido quando um mais novo o substitui. O `| undefined` é explícito
   * porque `exactOptionalPropertyTypes` distingue "ausente" de "presente e indefinido", e
   * quem chama nem sempre tem um sinal para passar.
   */
  signal?: AbortSignal | undefined
}

/**
 * Distingue "o pedido foi cancelado porque outro o substituiu" de "a requisição falhou".
 * Sem isso, trocar de filtro depressa pintaria a tela de erro.
 */
/**
 * Resolve o caminho de um arquivo servido pelo backend (`/uploads/...`) contra a base da API.
 *
 * O backend devolve caminho relativo de propósito: o que está gravado no banco continua
 * válido se o domínio mudar. Em desenvolvimento o proxy do Vite encaminha `/uploads`, então
 * o relativo já funciona; com o frontend hospedado separado do backend, `<img src="/uploads/…">`
 * bateria no domínio do frontend e voltaria 404 — ou, pior, o HTML do index.
 */
export function urlDeArquivo(caminho: string): string {
  if (/^https?:\/\//.test(caminho)) {
    return caminho
  }
  return BASE + caminho
}

export function foiCancelada(erro: unknown): boolean {
  return erro instanceof DOMException && erro.name === 'AbortError'
}

/**
 * O retorno é tipado pelo chamador. O `as T` é a única asserção da camada: a resposta vem de
 * fora e o TypeScript não pode verificá-la em tempo de compilação — é o contrato de
 * `types/api.ts` que garante o acerto, e ele é derivado dos DTOs do backend.
 */
async function requisicao<T>(
  metodo: Metodo,
  caminho: string,
  { corpo, formData, signal }: OpcoesRequisicao = {},
): Promise<T> {
  const corpoDaRequisicao = formData ?? (corpo !== undefined ? JSON.stringify(corpo) : null)

  const resposta = await fetch(`${BASE}${caminho}`, {
    method: metodo,
    headers: cabecalhos(corpo !== undefined),
    body: corpoDaRequisicao,
    signal: signal ?? null,
  })
  return (await tratarResposta(resposta)) as T
}

export const api = {
  get: <T>(caminho: string, opcoes?: { signal?: AbortSignal | undefined }): Promise<T> =>
    requisicao<T>('GET', caminho, opcoes),

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
