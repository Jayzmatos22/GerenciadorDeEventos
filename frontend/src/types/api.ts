/**
 * Contrato da API, espelhando os DTOs do backend (seção 6 da especificação técnica).
 *
 * Duas convenções do backend que explicam o formato destes tipos:
 *
 * - `spring.jackson.default-property-inclusion: non_null` — campo nulo é **omitido** do JSON,
 *   não vem como `null`. Por isso os opcionais são `campo?: T` e não `T | null`.
 * - Datas trafegam como ISO-8601 local, sem fuso (`2026-11-10T19:00:00`), porque o sistema
 *   opera em `America/Sao_Paulo`, fixado no `application.yml`.
 */

/** ISO-8601 local, sem fuso. Alias nomeado para deixar claro o que a string carrega. */
export type DataHoraIso = string

// ---------------------------------------------------------------- enums do domínio

export type Papel = 'PARTICIPANTE' | 'ORGANIZADOR'

export type StatusEvento =
  | 'RASCUNHO'
  | 'PUBLICADO'
  | 'EM_ANDAMENTO'
  | 'ENCERRADO'
  | 'CANCELADO'

export type StatusInscricao = 'CONFIRMADA' | 'EM_ESPERA' | 'CANCELADA' | 'AUSENTE'

// ---------------------------------------------------------------- envelope de erro

/**
 * Códigos que o backend emite. Os oito primeiros são os previstos na especificação; os
 * demais são refinamentos de `REGRA_DE_NEGOCIO` com o mesmo status HTTP.
 *
 * A união com `(string & {})` é deliberada: um código novo no backend não quebra a
 * compilação do frontend, mas o editor continua completando os conhecidos.
 */
export type CodigoErro =
  | 'CREDENCIAIS_INVALIDAS'
  | 'ACESSO_NEGADO'
  | 'RECURSO_NAO_ENCONTRADO'
  | 'INSCRICAO_DUPLICADA'
  | 'VALIDACAO'
  | 'REGRA_DE_NEGOCIO'
  | 'PRESENCA_NAO_REGISTRADA'
  | 'IA_INDISPONIVEL'
  | 'EMAIL_EM_USO'
  | 'PERIODO_INVALIDO'
  | 'EVENTO_NAO_EDITAVEL'
  | 'TRANSICAO_INVALIDA'
  | 'LIMITE_ABAIXO_DAS_CONFIRMADAS'
  | 'ARQUIVO_INVALIDO'
  | 'EVENTO_NAO_ABERTO'
  | 'INSCRICAO_NAO_ATIVA'
  | 'CONFLITO_DE_CONCORRENCIA'
  | 'INSCRICAO_DE_OUTRO_EVENTO'
  | 'INSCRICAO_NAO_CONFIRMADA'
  | 'FORA_DA_JANELA_DE_CHECKIN'
  | 'FALHA_AO_GERAR_CERTIFICADO'
  | 'EVENTO_NAO_TERMINADO'
  | 'AVALIACAO_JA_ENVIADA'
  | 'AVALIACOES_INSUFICIENTES'
  | 'SEM_COMENTARIOS_PARA_RESUMIR'
  | 'ERRO_INTERNO'
  | (string & {})

export interface CampoInvalido {
  campo: string
  erro: string
}

export interface ErroResponse {
  timestamp: DataHoraIso
  status: number
  codigo: CodigoErro
  mensagem: string
  caminho: string
  /** Só vem presente quando `codigo` é `VALIDACAO`. */
  campos?: CampoInvalido[]
}

// ---------------------------------------------------------------- paginação

/** Envelope padrão do Spring Data. */
export interface Pagina<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  empty: boolean
}

// ---------------------------------------------------------------- usuário e sessão

export interface UsuarioResponse {
  id: number
  nome: string
  email: string
  papeis: Papel[]
  criadoEm: DataHoraIso
}

export interface LoginRequest {
  email: string
  senha: string
}

export interface LoginResponse {
  token: string
  expiraEm: DataHoraIso
  usuario: UsuarioResponse
}

export interface RegistrarRequest {
  nome: string
  email: string
  senha: string
  /** Ausente nasce `PARTICIPANTE`. */
  papel?: Papel
}

// ---------------------------------------------------------------- evento

export interface EventoFotoResponse {
  id: number
  url: string
  ordem: number
}

/** Item da listagem: o suficiente para montar um card. */
export interface EventoResumoResponse {
  id: number
  titulo: string
  local: string
  dataInicio: DataHoraIso
  dataFim: DataHoraIso
  cargaHoraria: number
  limiteVagas: number
  vagasRestantes: number
  status: StatusEvento
}

export interface EventoResponse {
  id: number
  titulo: string
  descricao: string
  local: string
  dataInicio: DataHoraIso
  dataFim: DataHoraIso
  cargaHoraria: number
  limiteVagas: number
  vagasRestantes: number
  status: StatusEvento
  criadoEm: DataHoraIso
  organizador: { id: number; nome: string }
  fotos: EventoFotoResponse[]
}

export interface EventoRequest {
  titulo: string
  descricao: string
  local: string
  dataInicio: DataHoraIso
  dataFim: DataHoraIso
  cargaHoraria: number
  limiteVagas: number
}

export interface AtualizarStatusRequest {
  status: StatusEvento
}

// ---------------------------------------------------------------- inscrição

/** Visão do participante: a inscrição e o evento ao qual ela pertence. */
export interface InscricaoResponse {
  id: number
  status: StatusInscricao
  /** Presente apenas em `EM_ESPERA`. */
  posicaoFila?: number
  dataInscricao: DataHoraIso
  evento: {
    id: number
    titulo: string
    local: string
    dataInicio: DataHoraIso
    dataFim: DataHoraIso
    cargaHoraria: number
    status: StatusEvento
  }
}

/** Visão do organizador: quem está inscrito e em que posição da fila. */
export interface InscritoResponse {
  id: number
  status: StatusInscricao
  posicaoFila?: number
  dataInscricao: DataHoraIso
  participante: { id: number; nome: string; email: string }
}

// ---------------------------------------------------------------- presença

export interface RegistrarPresencaRequest {
  inscricaoId: number
}

export interface PresencaResponse {
  id: number
  inscricaoId: number
  dataHoraCheckin: DataHoraIso
  participante: { id: number; nome: string; email: string }
}

// ---------------------------------------------------------------- certificado

export interface CertificadoResponse {
  id: number
  inscricaoId: number
  codigoAutenticidade: string
  dataEmissao: DataHoraIso
  urlValidacao: string
}

/** Resposta pública da validação: confirma o certificado sem expor dados de contato. */
export interface ValidacaoCertificadoResponse {
  codigoAutenticidade: string
  nomeParticipante: string
  tituloEvento: string
  cargaHoraria: number
  dataInicio: DataHoraIso
  dataFim: DataHoraIso
  dataEmissao: DataHoraIso
}

// ---------------------------------------------------------------- avaliação

export interface AvaliacaoRequest {
  nota: number
  comentario?: string | null
}

export interface AvaliacaoResponse {
  id: number
  inscricaoId: number
  nota: number
  comentario?: string
  dataEnvio: DataHoraIso
  nomeParticipante: string
}

export interface ResumoAvaliacaoResponse {
  eventoId: number
  textoResumo: string
  notaMedia?: number
  totalAvaliacoes?: number
  geradoEm: DataHoraIso
}

// ---------------------------------------------------------------- IA

export interface InterpretarEventoRequest {
  texto: string
}

/**
 * Rascunho devolvido por `POST /api/eventos/interpretar`. Nada é persistido: o organizador
 * revisa e só então chama `POST /api/eventos` (RF-03).
 *
 * Todo campo é opcional porque o backend devolve `null` — e portanto omite — o que o texto
 * não trazia. O que faltou vem nomeado em `camposNaoIdentificados`.
 */
export interface EventoExtraidoDTO {
  titulo?: string
  descricao?: string
  local?: string
  dataInicio?: DataHoraIso
  dataFim?: DataHoraIso
  cargaHoraria?: number
  limiteVagas?: number
  camposNaoIdentificados: CampoExtraido[]
}

/** Os campos que a extração sabe nomear, iguais aos do formulário de evento. */
export type CampoExtraido =
  | 'titulo'
  | 'descricao'
  | 'local'
  | 'dataInicio'
  | 'dataFim'
  | 'cargaHoraria'
  | 'limiteVagas'
