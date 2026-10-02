import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, query } from '../api/client'
import {
  Alerta,
  Botao,
  Campo,
  Cartao,
  Carregando,
  Entrada,
  Etiqueta,
  Paginacao,
  Vazio,
} from '../components/ui'
import { periodoDoEvento } from '../utils/formato'

function CartaoEvento({ evento }) {
  const lotado = evento.vagasRestantes === 0

  return (
    <Link to={`/eventos/${evento.id}`} className="block focus:outline-none">
      <Cartao className="h-full transition hover:ring-marinho-300">
        <div className="mb-2 flex items-start justify-between gap-3">
          <h2 className="font-semibold text-slate-900">{evento.titulo}</h2>
          <Etiqueta status={evento.status} />
        </div>

        <dl className="space-y-1 text-sm text-slate-600">
          <div>
            <dt className="sr-only">Local</dt>
            <dd>{evento.local}</dd>
          </div>
          <div>
            <dt className="sr-only">Período</dt>
            <dd>{periodoDoEvento(evento.dataInicio, evento.dataFim)}</dd>
          </div>
          <div>
            <dt className="sr-only">Carga horária</dt>
            <dd>{evento.cargaHoraria} h</dd>
          </div>
        </dl>

        <p className={`mt-3 text-sm font-medium ${lotado ? 'text-amber-700' : 'text-emerald-700'}`}>
          {lotado
            ? 'Sem vagas — inscrição entra na fila de espera'
            : `${evento.vagasRestantes} de ${evento.limiteVagas} vagas disponíveis`}
        </p>
      </Cartao>
    </Link>
  )
}

export default function Catalogo() {
  const [filtros, setFiltros] = useState({ q: '', dataInicio: '', dataFim: '' })
  const [aplicados, setAplicados] = useState({ q: '', dataInicio: '', dataFim: '' })
  const [pagina, setPagina] = useState(0)
  const [resultado, setResultado] = useState(null)
  const [erro, setErro] = useState(null)
  const [carregando, setCarregando] = useState(true)

  const buscar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      const parametros = query({
        q: aplicados.q,
        dataInicio: aplicados.dataInicio ? `${aplicados.dataInicio}T00:00:00` : '',
        dataFim: aplicados.dataFim ? `${aplicados.dataFim}T23:59:59` : '',
        page: pagina,
        size: 9,
      })
      setResultado(await api.get(`/api/eventos${parametros}`))
    } catch {
      setErro('Não foi possível carregar os eventos agora.')
    } finally {
      setCarregando(false)
    }
  }, [aplicados, pagina])

  useEffect(() => {
    buscar()
  }, [buscar])

  function aplicarFiltros(evento) {
    evento.preventDefault()
    setPagina(0)
    setAplicados(filtros)
  }

  function limparFiltros() {
    const vazios = { q: '', dataInicio: '', dataFim: '' }
    setFiltros(vazios)
    setAplicados(vazios)
    setPagina(0)
  }

  function alterar(campo) {
    return (evento) => setFiltros((anterior) => ({ ...anterior, [campo]: evento.target.value }))
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Eventos abertos</h1>
        <p className="mt-1 text-sm text-slate-600">
          Palestras, oficinas e encontros acadêmicos com inscrição aberta.
        </p>
      </header>

      <Cartao>
        <form onSubmit={aplicarFiltros} className="grid gap-4 sm:grid-cols-4">
          <div className="sm:col-span-2">
            <Campo rotulo="Buscar">
              <Entrada
                value={filtros.q}
                onChange={alterar('q')}
                placeholder="Título, descrição ou local"
              />
            </Campo>
          </div>
          <Campo rotulo="A partir de">
            <Entrada type="date" value={filtros.dataInicio} onChange={alterar('dataInicio')} />
          </Campo>
          <Campo rotulo="Até">
            <Entrada type="date" value={filtros.dataFim} onChange={alterar('dataFim')} />
          </Campo>

          <div className="flex gap-2 sm:col-span-4">
            <Botao type="submit">Filtrar</Botao>
            <Botao type="button" variante="discreto" onClick={limparFiltros}>
              Limpar
            </Botao>
          </div>
        </form>
      </Cartao>

      <Alerta>{erro}</Alerta>

      {carregando && <Carregando texto="Buscando eventos…" />}

      {!carregando && resultado?.content?.length === 0 && (
        <Vazio titulo="Nenhum evento encontrado">
          Ajuste os filtros ou volte mais tarde — novos eventos aparecem aqui assim que são
          publicados.
        </Vazio>
      )}

      {!carregando && resultado?.content?.length > 0 && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {resultado.content.map((evento) => (
              <CartaoEvento key={evento.id} evento={evento} />
            ))}
          </div>
          <Paginacao
            pagina={resultado.number}
            totalPaginas={resultado.totalPages}
            onMudar={setPagina}
          />
        </>
      )}
    </div>
  )
}
