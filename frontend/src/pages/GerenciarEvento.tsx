import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import {
  Alerta,
  Botao,
  BotaoLink,
  Cartao,
  Carregando,
  Etiqueta,
  Vazio,
} from '../components/ui'
import { formatarDataHora, periodoDoEvento } from '../utils/formato'
import type {
  AvaliacaoResponse,
  EventoFotoResponse,
  EventoResponse,
  InscritoResponse,
  Pagina,
  PresencaResponse,
  ResumoAvaliacaoResponse,
  StatusEvento,
} from '../types/api'
import type { ChangeEvent } from 'react'
import type { Variante } from '../components/ui'

interface Transicao {
  destino: StatusEvento
  rotulo: string
  variante?: Variante
}

const PROXIMOS_STATUS: Record<StatusEvento, Transicao[]> = {
  RASCUNHO: [
    { destino: 'PUBLICADO', rotulo: 'Publicar' },
    { destino: 'CANCELADO', rotulo: 'Cancelar evento', variante: 'perigo' },
  ],
  PUBLICADO: [
    { destino: 'EM_ANDAMENTO', rotulo: 'Marcar como em andamento' },
    { destino: 'ENCERRADO', rotulo: 'Encerrar' },
    { destino: 'CANCELADO', rotulo: 'Cancelar evento', variante: 'perigo' },
  ],
  EM_ANDAMENTO: [
    { destino: 'ENCERRADO', rotulo: 'Encerrar' },
    { destino: 'CANCELADO', rotulo: 'Cancelar evento', variante: 'perigo' },
  ],
  ENCERRADO: [],
  CANCELADO: [],
}

interface PainelInscritosProps {
  eventoId: string
  onMensagem: (mensagem: string) => void
}

function PainelInscritos({ eventoId, onMensagem }: PainelInscritosProps) {
  const [inscritos, setInscritos] = useState<InscritoResponse[]>([])
  const [presentes, setPresentes] = useState<PresencaResponse[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState<number | null>(null)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      const [pagina, listaDePresentes] = await Promise.all([
        api.get<Pagina<InscritoResponse>>(`/api/eventos/${eventoId}/inscricoes?page=0&size=100`),
        api.get<PresencaResponse[]>(`/api/eventos/${eventoId}/presencas`),
      ])
      setInscritos(pagina.content)
      setPresentes(listaDePresentes)
    } catch {
      setErro('Não foi possível carregar a lista de inscritos.')
    } finally {
      setCarregando(false)
    }
  }, [eventoId])

  useEffect(() => {
    void carregar()
  }, [carregar])

  const idsComPresenca = new Set(presentes.map((presenca) => presenca.inscricaoId))

  async function registrarPresenca(inscricaoId: number) {
    setErro(null)
    setOcupado(inscricaoId)
    try {
      await api.post<PresencaResponse>(`/api/eventos/${eventoId}/presencas`, { inscricaoId })
      onMensagem('Presença registrada.')
      await carregar()
    } catch (falha) {
      setErro(
        falha instanceof ApiError ? falha.message : 'Não foi possível registrar a presença.',
      )
    } finally {
      setOcupado(null)
    }
  }

  if (carregando) return <Carregando texto="Carregando inscritos…" />

  const confirmados = inscritos.filter((i) => i.status === 'CONFIRMADA')
  const fila = inscritos.filter((i) => i.status === 'EM_ESPERA')

  return (
    <div className="space-y-6">
      <Alerta>{erro}</Alerta>

      <section>
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Confirmados ({confirmados.length}) · presentes ({idsComPresenca.size})
        </h3>

        {confirmados.length === 0 ? (
          <Vazio titulo="Ninguém confirmado ainda" />
        ) : (
          <ul className="divide-y divide-slate-200 rounded-xl ring-1 ring-slate-200">
            {confirmados.map((inscrito) => {
              const presente = idsComPresenca.has(inscrito.id)
              return (
                <li
                  key={inscrito.id}
                  className="flex flex-wrap items-center justify-between gap-3 bg-white p-4 first:rounded-t-xl last:rounded-b-xl"
                >
                  <div>
                    <p className="font-medium text-slate-900">
                      {inscrito.participante.nome}
                    </p>
                    <p className="text-sm text-slate-500">{inscrito.participante.email}</p>
                  </div>

                  {presente ? (
                    <span className="text-sm font-medium text-emerald-700">
                      Presença registrada
                    </span>
                  ) : (
                    <Botao
                      variante="secundario"
                      onClick={() => {
                        void registrarPresenca(inscrito.id)
                      }}
                      disabled={ocupado === inscrito.id}
                    >
                      Registrar presença
                    </Botao>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <section>
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Fila de espera ({fila.length})
        </h3>

        {fila.length === 0 ? (
          <Vazio titulo="Fila vazia">
            Quando o evento lotar, novas inscrições entram aqui em ordem de chegada.
          </Vazio>
        ) : (
          <ol className="divide-y divide-slate-200 rounded-xl ring-1 ring-slate-200">
            {fila.map((inscrito) => (
              <li
                key={inscrito.id}
                className="flex items-center justify-between gap-3 bg-white p-4 first:rounded-t-xl last:rounded-b-xl"
              >
                <div>
                  <p className="font-medium text-slate-900">{inscrito.participante.nome}</p>
                  <p className="text-sm text-slate-500">{inscrito.participante.email}</p>
                </div>
                <span className="text-sm font-semibold text-amber-700">
                  {inscrito.posicaoFila}º
                </span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  )
}

function PainelAvaliacoes({ eventoId }: { eventoId: string }) {
  const [avaliacoes, setAvaliacoes] = useState<AvaliacaoResponse[]>([])
  const [resumo, setResumo] = useState<ResumoAvaliacaoResponse | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [avisoIa, setAvisoIa] = useState<string | null>(null)
  const [gerando, setGerando] = useState(false)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      setAvaliacoes(await api.get<AvaliacaoResponse[]>(`/api/eventos/${eventoId}/avaliacoes`))
    } catch {
      setErro('Não foi possível carregar as avaliações.')
    }

    try {
      setResumo(
        await api.get<ResumoAvaliacaoResponse>(`/api/eventos/${eventoId}/avaliacoes/resumo`),
      )
    } catch {
      // 404 é o caso normal de quem ainda não gerou resumo.
      setResumo(null)
    } finally {
      setCarregando(false)
    }
  }, [eventoId])

  useEffect(() => {
    void carregar()
  }, [carregar])

  async function gerarResumo() {
    setAvisoIa(null)
    setGerando(true)
    try {
      setResumo(
        await api.post<ResumoAvaliacaoResponse>(`/api/eventos/${eventoId}/avaliacoes/resumo`),
      )
    } catch (falha) {
      setAvisoIa(
        falha instanceof ApiError
          ? falha.message
          : 'Não foi possível gerar o resumo agora.',
      )
    } finally {
      setGerando(false)
    }
  }

  if (carregando) return <Carregando texto="Carregando avaliações…" />

  return (
    <div className="space-y-6">
      <Alerta>{erro}</Alerta>

      <Cartao className="bg-marinho-50/60">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="font-semibold text-marinho-900">Resumo por IA</h3>
            <p className="text-sm text-marinho-900/80">
              Precisa de pelo menos 3 avaliações com comentário. Gerar de novo substitui o
              resumo anterior.
            </p>
          </div>
          <Botao
            onClick={() => {
              void gerarResumo()
            }}
            disabled={gerando}
          >
            {gerando ? 'Gerando…' : resumo ? 'Gerar novamente' : 'Gerar resumo'}
          </Botao>
        </div>

        <div className="mt-3">
          <Alerta tom="aviso">{avisoIa}</Alerta>
        </div>

        {resumo && (
          <div className="mt-4 rounded-lg bg-white p-4 ring-1 ring-marinho-200">
            <p className="text-sm leading-relaxed text-slate-700">{resumo.textoResumo}</p>
            <p className="mt-3 text-xs text-slate-500">
              Nota média {resumo.notaMedia} · {resumo.totalAvaliacoes} avaliações · gerado em{' '}
              {formatarDataHora(resumo.geradoEm)}
            </p>
          </div>
        )}
      </Cartao>

      <section>
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Avaliações recebidas ({avaliacoes.length})
        </h3>

        {avaliacoes.length === 0 ? (
          <Vazio titulo="Nenhuma avaliação ainda">
            Elas só podem ser enviadas depois que o evento termina, por quem teve presença
            registrada.
          </Vazio>
        ) : (
          <ul className="space-y-3">
            {avaliacoes.map((avaliacao) => (
              <li key={avaliacao.id}>
                <Cartao>
                  <div className="flex items-start justify-between gap-3">
                    <p className="font-medium text-slate-900">{avaliacao.nomeParticipante}</p>
                    <span className="text-sm font-semibold text-marinho-700">
                      {avaliacao.nota}/5
                    </span>
                  </div>
                  {avaliacao.comentario && (
                    <p className="mt-2 text-sm text-slate-700">{avaliacao.comentario}</p>
                  )}
                  <p className="mt-2 text-xs text-slate-500">
                    {formatarDataHora(avaliacao.dataEnvio)}
                  </p>
                </Cartao>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

interface PainelFotosProps {
  eventoId: string
  fotos: EventoFotoResponse[]
  onMudou: () => Promise<void>
  onMensagem: (mensagem: string) => void
}

function PainelFotos({ eventoId, fotos, onMudou, onMensagem }: PainelFotosProps) {
  const [erro, setErro] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  async function enviar(evento: ChangeEvent<HTMLInputElement>) {
    const arquivo = evento.target.files?.[0]
    if (!arquivo) return

    setErro(null)
    setOcupado(true)
    try {
      const formData = new FormData()
      formData.append('arquivo', arquivo)
      await api.upload<EventoFotoResponse>(`/api/eventos/${eventoId}/fotos`, formData)
      onMensagem('Foto adicionada.')
      await onMudou()
    } catch (falha) {
      setErro(falha instanceof ApiError ? falha.message : 'Não foi possível enviar a foto.')
    } finally {
      setOcupado(false)
      evento.target.value = ''
    }
  }

  async function remover(fotoId: number) {
    setErro(null)
    setOcupado(true)
    try {
      await api.delete(`/api/eventos/${eventoId}/fotos/${fotoId}`)
      onMensagem('Foto removida.')
      await onMudou()
    } catch (falha) {
      setErro(falha instanceof ApiError ? falha.message : 'Não foi possível remover a foto.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <div className="space-y-4">
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">
          Adicionar foto (jpg, jpeg, png ou webp)
        </span>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={(evento) => {
            void enviar(evento)
          }}
          disabled={ocupado}
          className="block w-full text-sm text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-marinho-600 file:px-4 file:py-2 file:text-sm file:font-medium file:text-white hover:file:bg-marinho-700"
        />
      </label>

      <Alerta>{erro}</Alerta>

      {fotos.length === 0 ? (
        <Vazio titulo="Nenhuma foto enviada" />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-3">
          {fotos.map((foto) => (
            <li key={foto.id} className="space-y-2">
              <img
                src={foto.url}
                alt="Foto do evento"
                className="h-36 w-full rounded-xl object-cover ring-1 ring-slate-200"
                loading="lazy"
              />
              <Botao
                variante="discreto"
                onClick={() => {
                  void remover(foto.id)
                }}
                disabled={ocupado}
                className="w-full"
              >
                Remover
              </Botao>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

type Aba = 'inscritos' | 'avaliacoes' | 'fotos'

const ABAS: { chave: Aba; rotulo: string }[] = [
  { chave: 'inscritos', rotulo: 'Inscritos e presença' },
  { chave: 'avaliacoes', rotulo: 'Avaliações' },
  { chave: 'fotos', rotulo: 'Fotos' },
]

export default function GerenciarEvento() {
  const { id = '' } = useParams()

  const [evento, setEvento] = useState<EventoResponse | null>(null)
  const [aba, setAba] = useState<Aba>('inscritos')
  const [erro, setErro] = useState<string | null>(null)
  const [mensagem, setMensagem] = useState<string | null>(null)
  const [mudandoStatus, setMudandoStatus] = useState(false)

  const carregar = useCallback(async () => {
    setErro(null)
    try {
      setEvento(await api.get<EventoResponse>(`/api/eventos/${id}`))
    } catch {
      setErro('Não foi possível carregar o evento.')
    }
  }, [id])

  useEffect(() => {
    void carregar()
  }, [carregar])

  async function mudarStatus(destino: StatusEvento) {
    setErro(null)
    setMudandoStatus(true)
    try {
      setEvento(await api.patch<EventoResponse>(`/api/eventos/${id}/status`, { status: destino }))
      setMensagem(`Evento agora está ${destino.replace('_', ' ').toLowerCase()}.`)
    } catch (falha) {
      setErro(falha instanceof ApiError ? falha.message : 'Não foi possível mudar o status.')
    } finally {
      setMudandoStatus(false)
    }
  }

  if (erro && !evento) return <Alerta titulo="Evento indisponível">{erro}</Alerta>
  if (!evento) return <Carregando texto="Carregando evento…" />

  const transicoes = PROXIMOS_STATUS[evento.status]
  const editavel = evento.status !== 'ENCERRADO' && evento.status !== 'CANCELADO'

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <Link to="/organizador" className="text-sm text-marinho-700 hover:underline">
          ← Meus eventos
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">{evento.titulo}</h1>
            <p className="mt-1 text-sm text-slate-600">
              {periodoDoEvento(evento.dataInicio, evento.dataFim)} · {evento.local}
            </p>
          </div>
          <Etiqueta status={evento.status} />
        </div>
      </header>

      <Alerta>{erro}</Alerta>
      <Alerta tom="sucesso">{mensagem}</Alerta>

      <Cartao>
        <div className="flex flex-wrap items-center gap-2">
          {editavel && (
            <BotaoLink variante="secundario" to={`/organizador/eventos/${id}/editar`}>
              Editar dados
            </BotaoLink>
          )}
          {transicoes.map(({ destino, rotulo, variante }) => (
            <Botao
              key={destino}
              variante={variante ?? 'primario'}
              onClick={() => {
                void mudarStatus(destino)
              }}
              disabled={mudandoStatus}
            >
              {rotulo}
            </Botao>
          ))}
          {transicoes.length === 0 && (
            <p className="text-sm text-slate-600">
              Este evento está em um estado final e não aceita mais mudanças.
            </p>
          )}
        </div>
      </Cartao>

      <nav className="flex gap-1 border-b border-slate-200" aria-label="Seções do evento">
        {ABAS.map(({ chave, rotulo }) => (
          <button
            key={chave}
            type="button"
            onClick={() => setAba(chave)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition ${
              aba === chave
                ? 'border-marinho-600 text-marinho-700'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {rotulo}
          </button>
        ))}
      </nav>

      {aba === 'inscritos' && <PainelInscritos eventoId={id} onMensagem={setMensagem} />}
      {aba === 'avaliacoes' && <PainelAvaliacoes eventoId={id} />}
      {aba === 'fotos' && (
        <PainelFotos
          eventoId={id}
          fotos={evento.fotos}
          onMudou={carregar}
          onMensagem={setMensagem}
        />
      )}
    </div>
  )
}
