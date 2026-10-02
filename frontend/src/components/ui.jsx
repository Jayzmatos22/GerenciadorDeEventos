import { Link } from 'react-router-dom'

const VARIANTES = {
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

export function Botao({ variante = 'primario', className = '', ...resto }) {
  return <button className={`${BASE_BOTAO} ${VARIANTES[variante]} ${className}`} {...resto} />
}

export function BotaoLink({ variante = 'primario', className = '', ...resto }) {
  return <Link className={`${BASE_BOTAO} ${VARIANTES[variante]} ${className}`} {...resto} />
}

export function Campo({ rotulo, erro, dica, children }) {
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

export function Entrada({ erro, className = '', ...resto }) {
  return (
    <input
      className={`${BASE_ENTRADA} ${erro ? 'border-rose-400' : ''} ${className}`}
      {...resto}
    />
  )
}

export function AreaTexto({ erro, className = '', ...resto }) {
  return (
    <textarea
      className={`${BASE_ENTRADA} ${erro ? 'border-rose-400' : ''} ${className}`}
      {...resto}
    />
  )
}

export function Selecao({ className = '', ...resto }) {
  return <select className={`${BASE_ENTRADA} ${className}`} {...resto} />
}

const TONS_ALERTA = {
  erro: 'bg-rose-50 text-rose-800 ring-rose-200',
  aviso: 'bg-amber-50 text-amber-900 ring-amber-200',
  sucesso: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  info: 'bg-marinho-50 text-marinho-900 ring-marinho-200',
}

export function Alerta({ tom = 'erro', titulo, children }) {
  if (!titulo && !children) return null
  return (
    <div className={`rounded-lg p-3 text-sm ring-1 ${TONS_ALERTA[tom]}`} role="alert">
      {titulo && <p className="font-semibold">{titulo}</p>}
      {children && <div className={titulo ? 'mt-1' : ''}>{children}</div>}
    </div>
  )
}

const TONS_STATUS = {
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

export function Etiqueta({ status }) {
  const tom = TONS_STATUS[status] ?? 'bg-slate-100 text-slate-700'
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${tom}`}>
      {String(status).replace('_', ' ')}
    </span>
  )
}

export function Cartao({ className = '', children }) {
  return (
    <div className={`rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 ${className}`}>
      {children}
    </div>
  )
}

export function Carregando({ texto = 'Carregando…' }) {
  return <p className="py-10 text-center text-sm text-slate-500">{texto}</p>
}

export function Vazio({ titulo, children }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 p-10 text-center">
      <p className="font-medium text-slate-700">{titulo}</p>
      {children && <p className="mt-1 text-sm text-slate-500">{children}</p>}
    </div>
  )
}

export function Paginacao({ pagina, totalPaginas, onMudar }) {
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
