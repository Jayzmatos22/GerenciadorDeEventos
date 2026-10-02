import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react'
import { Link, type LinkProps } from 'react-router-dom'
import type { StatusEvento, StatusInscricao } from '../types/api'

export type Variante = 'primario' | 'secundario' | 'perigo' | 'discreto'

const VARIANTES: Record<Variante, string> = {
  primario: 'bg-marinho-600 text-white hover:bg-marinho-700 focus-visible:outline-marinho-600',
  secundario:
    'bg-white text-marinho-700 ring-1 ring-marinho-200 hover:bg-marinho-50 focus-visible:outline-marinho-600',
  perigo: 'bg-rose-600 text-white hover:bg-rose-700 focus-visible:outline-rose-600',
  discreto: 'bg-slate-100 text-slate-700 hover:bg-slate-200 focus-visible:outline-slate-500',
}

const BASE_BOTAO =
  'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium ' +
  'transition focus-visible:outline-2 focus-visible:outline-offset-2 ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

type BotaoProps = ButtonHTMLAttributes<HTMLButtonElement> & { variante?: Variante }

export function Botao({ variante = 'primario', className = '', ...resto }: BotaoProps) {
  return <button className={`${BASE_BOTAO} ${VARIANTES[variante]} ${className}`} {...resto} />
}

type BotaoLinkProps = LinkProps & { variante?: Variante }

export function BotaoLink({ variante = 'primario', className = '', ...resto }: BotaoLinkProps) {
  return <Link className={`${BASE_BOTAO} ${VARIANTES[variante]} ${className}`} {...resto} />
}

interface CampoProps {
  rotulo: string
  erro?: string | undefined
  dica?: string | undefined
  children: ReactNode
}

export function Campo({ rotulo, erro, dica, children }: CampoProps) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{rotulo}</span>
      {children}
      {dica && !erro && <span className="mt-1 block text-xs text-slate-500">{dica}</span>}
      {erro && <span className="mt-1 block text-xs font-medium text-rose-600">{erro}</span>}
    </label>
  )
}

const BASE_ENTRADA =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 ' +
  'placeholder:text-slate-400 focus:border-marinho-500 focus:outline-none focus:ring-2 ' +
  'focus:ring-marinho-100'

/** `erro` só pinta a borda; a mensagem em si é responsabilidade do {@link Campo}. */
type ComErro = { erro?: string | undefined }

export function Entrada({
  erro,
  className = '',
  ...resto
}: InputHTMLAttributes<HTMLInputElement> & ComErro) {
  return (
    <input
      className={`${BASE_ENTRADA} ${erro ? 'border-rose-400' : ''} ${className}`}
      {...resto}
    />
  )
}

export function AreaTexto({
  erro,
  className = '',
  ...resto
}: TextareaHTMLAttributes<HTMLTextAreaElement> & ComErro) {
  return (
    <textarea
      className={`${BASE_ENTRADA} ${erro ? 'border-rose-400' : ''} ${className}`}
      {...resto}
    />
  )
}

export function Selecao({
  className = '',
  ...resto
}: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={`${BASE_ENTRADA} ${className}`} {...resto} />
}

export type TomAlerta = 'erro' | 'aviso' | 'sucesso' | 'info'

const TONS_ALERTA: Record<TomAlerta, string> = {
  erro: 'bg-rose-50 text-rose-800 ring-rose-200',
  aviso: 'bg-amber-50 text-amber-900 ring-amber-200',
  sucesso: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  info: 'bg-marinho-50 text-marinho-900 ring-marinho-200',
}

interface AlertaProps {
  tom?: TomAlerta
  titulo?: string | null
  children?: ReactNode
}

/** Não renderiza nada quando não há o que dizer, para a tela poder chamá-lo incondicionalmente. */
export function Alerta({ tom = 'erro', titulo, children }: AlertaProps) {
  if (!titulo && !children) return null
  return (
    <div className={`rounded-lg p-3 text-sm ring-1 ${TONS_ALERTA[tom]}`} role="alert">
      {titulo && <p className="font-semibold">{titulo}</p>}
      {children && <div className={titulo ? 'mt-1' : ''}>{children}</div>}
    </div>
  )
}

const TONS_STATUS: Record<StatusEvento | StatusInscricao, string> = {
  RASCUNHO: 'bg-slate-100 text-slate-700',
  PUBLICADO: 'bg-emerald-100 text-emerald-800',
  EM_ANDAMENTO: 'bg-marinho-100 text-marinho-800',
  ENCERRADO: 'bg-slate-200 text-slate-700',
  CANCELADO: 'bg-rose-100 text-rose-800',
  CONFIRMADA: 'bg-emerald-100 text-emerald-800',
  EM_ESPERA: 'bg-amber-100 text-amber-900',
  CANCELADA: 'bg-rose-100 text-rose-800',
  AUSENTE: 'bg-slate-200 text-slate-700',
}

export function Etiqueta({ status }: { status: StatusEvento | StatusInscricao }) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${TONS_STATUS[status]}`}
    >
      {status.replace('_', ' ')}
    </span>
  )
}

export function Cartao({
  className = '',
  children,
}: {
  className?: string
  children: ReactNode
}) {
  return (
    <div className={`rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 ${className}`}>
      {children}
    </div>
  )
}

export function Carregando({ texto = 'Carregando…' }: { texto?: string }) {
  return <p className="py-10 text-center text-sm text-slate-500">{texto}</p>
}

export function Vazio({ titulo, children }: { titulo: string; children?: ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 p-10 text-center">
      <p className="font-medium text-slate-700">{titulo}</p>
      {children && <p className="mt-1 text-sm text-slate-500">{children}</p>}
    </div>
  )
}

interface PaginacaoProps {
  pagina: number
  totalPaginas: number
  onMudar: (pagina: number) => void
}

export function Paginacao({ pagina, totalPaginas, onMudar }: PaginacaoProps) {
  if (totalPaginas <= 1) return null
  return (
    <nav className="flex items-center justify-center gap-3 pt-2" aria-label="Paginação">
      <Botao variante="discreto" disabled={pagina === 0} onClick={() => onMudar(pagina - 1)}>
        Anterior
      </Botao>
      <span className="text-sm text-slate-600">
        Página {pagina + 1} de {totalPaginas}
      </span>
      <Botao
        variante="discreto"
        disabled={pagina + 1 >= totalPaginas}
        onClick={() => onMudar(pagina + 1)}
      >
        Próxima
      </Botao>
    </nav>
  )
}
