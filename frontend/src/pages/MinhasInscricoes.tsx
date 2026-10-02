import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, api, query } from '../api/client'
import {
  Alerta,
  AreaTexto,
  Botao,
  Campo,
  Cartao,
  Carregando,
  Etiqueta,
  Paginacao,
  Selecao,
  Vazio,
} from '../components/ui'
import { periodoDoEvento } from '../utils/formato'
import type { AvaliacaoResponse, CertificadoResponse, InscricaoResponse, Pagina } from '../types/api'

interface FormularioAvaliacaoProps {
  inscricaoId: number
  onAvaliada: (mensagem: string) => void
}

const ROTULOS_DE_NOTA = ['Ruim', 'Regular', 'Bom', 'Muito bom', 'Excelente'] as const

function FormularioAvaliacao({ inscricaoId, onAvaliada }: FormularioAvaliacaoProps) {
  const [nota, setNota] = useState('5')
  const [comentario, setComentario] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErro(null)
    setEnviando(true)
    try {
      await api.post<AvaliacaoResponse>(`/api/inscricoes/${inscricaoId}/avaliacao`, {
        nota: Number(nota),
        comentario: comentario.trim() || null,
      })
      onAvaliada('Avaliação enviada. Obrigado pelo retorno!')
    } catch (falha) {
      setErro(falha instanceof ApiError ? falha.message : 'Não foi possível enviar a avaliação.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={enviar} className="mt-4 space-y-3 border-t border-slate-200 pt-4">
      <Campo rotulo="Sua nota">
        <Selecao value={nota} onChange={(e) => setNota(e.target.value)}>
          {[5, 4, 3, 2, 1].map((valor) => (
            <option key={valor} value={valor}>
              {valor} — {ROTULOS_DE_NOTA[valor - 1]}
            </option>
          ))}
        </Selecao>
      </Campo>

      <Campo rotulo="Comentário (opcional)">
        <AreaTexto
          rows={3}
          value={comentario}
          onChange={(e) => setComentario(e.target.value)}
          placeholder="O que funcionou bem e o que poderia melhorar?"
        />
      </Campo>

      <Alerta>{erro}</Alerta>

      <Botao type="submit" disabled={enviando}>
        {enviando ? 'Enviando…' : 'Enviar avaliação'}
      </Botao>
    </form>
  )
}

interface CartaoInscricaoProps {
  inscricao: InscricaoResponse
  onMudou: () => Promise<void>
  onMensagem: (mensagem: string) => void
}

function CartaoInscricao({ inscricao, onMudou, onMensagem }: CartaoInscricaoProps) {
  const { evento } = inscricao
  const [avaliando, setAvaliando] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const ativa = inscricao.status === 'CONFIRMADA' || inscricao.status === 'EM_ESPERA'
  const eventoTerminou = new Date(evento.dataFim) < new Date()

  async function cancelar() {
    setErro(null)
    setOcupado(true)
    try {
      await api.delete(`/api/inscricoes/${inscricao.id}`)
      onMensagem('Inscrição cancelada. Se havia fila, a primeira pessoa foi promovida.')
      await onMudou()
    } catch (falha) {
      setErro(falha instanceof ApiError ? falha.message : 'Não foi possível cancelar agora.')
    } finally {
      setOcupado(false)
    }
  }

  async function baixarCertificado() {
    setErro(null)
    setOcupado(true)
    try {
      // A emissão é idempotente: chamar de novo devolve o mesmo certificado (RN-05).
      await api.post<CertificadoResponse>(`/api/inscricoes/${inscricao.id}/certificado`)
      const pdf = await api.getPdf(`/api/inscricoes/${inscricao.id}/certificado`)

      const url = URL.createObjectURL(pdf)
      const link = document.createElement('a')
      link.href = url
      link.download = `certificado-${inscricao.id}.pdf`
      link.click()
      URL.revokeObjectURL(url)
    } catch (falha) {
      setErro(
        falha instanceof ApiError
          ? falha.message
          : 'Não foi possível gerar o certificado agora.',
      )
    } finally {
      setOcupado(false)
    }
  }

  return (
    <Cartao>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-900">{evento.titulo}</h2>
          <p className="mt-1 text-sm text-slate-600">
            {periodoDoEvento(evento.dataInicio, evento.dataFim)} · {evento.local}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Etiqueta status={inscricao.status} />
          {inscricao.posicaoFila !== undefined && (
            <span className="text-xs font-medium text-amber-700">
              {inscricao.posicaoFila}º da fila
            </span>
          )}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        {ativa && (
          <Botao variante="secundario" onClick={cancelar} disabled={ocupado}>
            Cancelar inscrição
          </Botao>
        )}
        {eventoTerminou && inscricao.status === 'CONFIRMADA' && (
          <>
            <Botao variante="secundario" onClick={baixarCertificado} disabled={ocupado}>
              Baixar certificado
            </Botao>
            <Botao variante="discreto" onClick={() => setAvaliando((aberto) => !aberto)}>
              {avaliando ? 'Fechar avaliação' : 'Avaliar evento'}
            </Botao>
          </>
        )}
      </div>

      <div className="mt-3">
        <Alerta>{erro}</Alerta>
      </div>

      {avaliando && (
        <FormularioAvaliacao
          inscricaoId={inscricao.id}
          onAvaliada={(mensagem) => {
            setAvaliando(false)
            onMensagem(mensagem)
          }}
        />
      )}
    </Cartao>
  )
}

export default function MinhasInscricoes() {
  const [resultado, setResultado] = useState<Pagina<InscricaoResponse> | null>(null)
  const [pagina, setPagina] = useState(0)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [mensagem, setMensagem] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      setResultado(
        await api.get<Pagina<InscricaoResponse>>(
          `/api/inscricoes/minhas${query({ page: pagina, size: 10 })}`,
        ),
      )
    } catch {
      setErro('Não foi possível carregar suas inscrições agora.')
    } finally {
      setCarregando(false)
    }
  }, [pagina])

  useEffect(() => {
    carregar()
  }, [carregar])

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Minhas inscrições</h1>
        <p className="mt-1 text-sm text-slate-600">
          Certificado e avaliação ficam disponíveis depois que o evento termina e sua presença é
          registrada.
        </p>
      </header>

      <Alerta>{erro}</Alerta>
      <Alerta tom="sucesso">{mensagem}</Alerta>

      {carregando && <Carregando />}

      {!carregando && resultado?.content.length === 0 && (
        <Vazio titulo="Você ainda não se inscreveu em nenhum evento">
          Dê uma olhada nos eventos abertos e garanta sua vaga.
        </Vazio>
      )}

      {!carregando && resultado && resultado.content.length > 0 && (
        <>
          <div className="space-y-4">
            {resultado.content.map((inscricao) => (
              <CartaoInscricao
                key={inscricao.id}
                inscricao={inscricao}
                onMudou={carregar}
                onMensagem={setMensagem}
              />
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
