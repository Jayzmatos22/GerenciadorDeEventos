import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, query } from '../api/client'
import type { EventoResumoResponse, Pagina } from '../types/api'
import {
  Alerta,
  BotaoLink,
  Cartao,
  Carregando,
  Etiqueta,
  Paginacao,
  Vazio,
} from '../components/ui'
import { periodoDoEvento } from '../utils/formato'

export default function MeusEventos() {
  const [resultado, setResultado] = useState<Pagina<EventoResumoResponse> | null>(null)
  const [pagina, setPagina] = useState(0)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    try {
      setResultado(
        await api.get<Pagina<EventoResumoResponse>>(
          `/api/eventos/meus${query({ page: pagina, size: 10 })}`,
        ),
      )
      setErro(null)
    } catch {
      setErro('Não foi possível carregar seus eventos agora.')
    } finally {
      setCarregando(false)
    }
  }, [pagina])

  useEffect(() => {
    void carregar()
  }, [carregar])

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Meus eventos</h1>
          <p className="mt-1 text-sm text-slate-600">
            Rascunhos, eventos publicados e encerrados que você organiza.
          </p>
        </div>
        <BotaoLink to="/organizador/eventos/novo">Novo evento</BotaoLink>
      </header>

      <Alerta>{erro}</Alerta>

      {carregando && <Carregando />}

      {!carregando && resultado?.content.length === 0 && (
        <Vazio titulo="Você ainda não criou nenhum evento">
          Comece colando o texto do convite e deixe a IA preencher o rascunho para você revisar.
        </Vazio>
      )}

      {!carregando && resultado && resultado.content.length > 0 && (
        <>
          <div className="space-y-4">
            {resultado.content.map((evento) => (
              <Cartao key={evento.id}>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <Link
                      to={`/organizador/eventos/${evento.id}`}
                      className="font-semibold text-slate-900 hover:text-marinho-700"
                    >
                      {evento.titulo}
                    </Link>
                    <p className="mt-1 text-sm text-slate-600">
                      {periodoDoEvento(evento.dataInicio, evento.dataFim)} · {evento.local}
                    </p>
                    <p className="mt-1 text-sm text-slate-600">
                      {evento.limiteVagas - evento.vagasRestantes} de {evento.limiteVagas} vagas
                      ocupadas
                    </p>
                  </div>
                  <Etiqueta status={evento.status} />
                </div>
              </Cartao>
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
