import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { EventoResponse, InscricaoResponse } from '../types/api'
import type { EstadoDeRota } from '../types/rota'
import { useAuth } from '../context/auth'
import { Alerta, Botao, Cartao, Carregando, Etiqueta } from '../components/ui'
import { periodoDoEvento } from '../utils/formato'

export default function DetalheEvento() {
  const { id } = useParams()
  const navegar = useNavigate()
  const { autenticado, ehParticipante } = useAuth()

  const [evento, setEvento] = useState<EventoResponse | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [inscrevendo, setInscrevendo] = useState(false)

  const carregar = useCallback(async () => {
    try {
      setEvento(await api.get<EventoResponse>(`/api/eventos/${id}`))
      setErro(null)
    } catch (falha) {
      setErro(
        falha instanceof ApiError && falha.status === 404
          ? 'Este evento não existe ou foi removido.'
          : 'Não foi possível carregar o evento agora.',
      )
    }
  }, [id])

  useEffect(() => {
    void carregar()
  }, [carregar])

  async function inscrever() {
    if (!autenticado) {
      void navegar('/entrar', { state: { de: `/eventos/${id}` } satisfies EstadoDeRota })
      return
    }

    setAviso(null)
    setSucesso(null)
    setInscrevendo(true)
    try {
      const inscricao = await api.post<InscricaoResponse>(`/api/eventos/${id}/inscricoes`)
      setSucesso(
        inscricao.status === 'CONFIRMADA'
          ? 'Inscrição confirmada. Você já está na lista de participantes.'
          : `O evento está lotado, então você entrou na fila de espera na posição ${inscricao.posicaoFila}. Se alguém cancelar, a vaga é sua.`,
      )
      await carregar()
    } catch (falha) {
      setAviso(
        falha instanceof ApiError ? falha.message : 'Não foi possível concluir a inscrição.',
      )
    } finally {
      setInscrevendo(false)
    }
  }

  if (erro) return <Alerta titulo="Evento indisponível">{erro}</Alerta>
  if (!evento) return <Carregando texto="Carregando evento…" />

  const lotado = evento.vagasRestantes === 0
  const aberto = evento.status === 'PUBLICADO'

  return (
    <article className="space-y-6">
      <header className="space-y-2">
        <Etiqueta status={evento.status} />
        <h1 className="text-3xl font-semibold text-slate-900">{evento.titulo}</h1>
        <p className="text-sm text-slate-600">
          Organizado por {evento.organizador.nome}
        </p>
      </header>

      {evento.fotos.length > 0 && (
        <div className="grid gap-3 sm:grid-cols-3">
          {evento.fotos.map((foto) => (
            <img
              key={foto.id}
              src={foto.url}
              alt={`Foto do evento ${evento.titulo}`}
              className="h-40 w-full rounded-xl object-cover ring-1 ring-slate-200"
              loading="lazy"
            />
          ))}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Cartao>
            <h2 className="mb-2 font-semibold text-slate-900">Sobre o evento</h2>
            <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">
              {evento.descricao}
            </p>
          </Cartao>
        </div>

        <Cartao className="h-fit space-y-4">
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="font-medium text-slate-500">Quando</dt>
              <dd className="text-slate-800">
                {periodoDoEvento(evento.dataInicio, evento.dataFim)}
              </dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Onde</dt>
              <dd className="text-slate-800">{evento.local}</dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Carga horária</dt>
              <dd className="text-slate-800">{evento.cargaHoraria} hora(s)</dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Vagas</dt>
              <dd className="text-slate-800">
                {evento.vagasRestantes} de {evento.limiteVagas} disponíveis
              </dd>
            </div>
          </dl>

          {aberto && (!autenticado || ehParticipante) && (
            <Botao
              onClick={() => {
                void inscrever()
              }}
              disabled={inscrevendo}
              className="w-full"
            >
              {inscrevendo
                ? 'Enviando…'
                : lotado
                  ? 'Entrar na fila de espera'
                  : 'Quero me inscrever'}
            </Botao>
          )}

          {aberto && autenticado && !ehParticipante && (
            <Alerta tom="info">
              Sua conta é de organizador. Para se inscrever em eventos, use uma conta de
              participante.
            </Alerta>
          )}

          {!aberto && (
            <Alerta tom="aviso">
              As inscrições deste evento não estão abertas.
            </Alerta>
          )}

          <Alerta tom="sucesso">{sucesso}</Alerta>
          <Alerta tom="aviso">{aviso}</Alerta>
        </Cartao>
      </div>
    </article>
  )
}
