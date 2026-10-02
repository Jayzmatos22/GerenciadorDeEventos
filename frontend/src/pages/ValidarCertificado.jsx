import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { Alerta, Botao, Campo, Cartao, Entrada } from '../components/ui'
import { formatarData, periodoDoEvento } from '../utils/formato'

export default function ValidarCertificado() {
  const { codigo: codigoDaUrl } = useParams()
  const navegar = useNavigate()

  const [codigo, setCodigo] = useState(codigoDaUrl ?? '')
  const [certificado, setCertificado] = useState(null)
  const [erro, setErro] = useState(null)
  const [consultando, setConsultando] = useState(false)

  useEffect(() => {
    if (!codigoDaUrl) return

    let cancelado = false
    setConsultando(true)
    api
      .get(`/api/certificados/validar/${codigoDaUrl}`)
      .then((dados) => {
        if (!cancelado) {
          setCertificado(dados)
          setErro(null)
        }
      })
      .catch((falha) => {
        if (!cancelado) {
          setCertificado(null)
          setErro(
            falha instanceof ApiError && falha.status === 404
              ? 'Nenhum certificado corresponde a este código.'
              : 'Não foi possível validar o certificado agora.',
          )
        }
      })
      .finally(() => {
        if (!cancelado) setConsultando(false)
      })

    return () => {
      cancelado = true
    }
  }, [codigoDaUrl])

  function consultar(evento) {
    evento.preventDefault()
    const limpo = codigo.trim().toUpperCase()
    if (limpo) navegar(`/certificados/validar/${limpo}`)
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Validar certificado</h1>
        <p className="mt-1 text-sm text-slate-600">
          Informe o código de autenticidade impresso no certificado. A consulta é pública.
        </p>
      </header>

      <Cartao>
        <form onSubmit={consultar} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <Campo rotulo="Código de autenticidade">
              <Entrada
                value={codigo}
                onChange={(e) => setCodigo(e.target.value)}
                placeholder="32 caracteres, sem hífens"
                className="font-mono uppercase"
                required
              />
            </Campo>
          </div>
          <Botao type="submit" disabled={consultando}>
            {consultando ? 'Consultando…' : 'Validar'}
          </Botao>
        </form>
      </Cartao>

      <Alerta titulo={erro ? 'Certificado não encontrado' : null}>{erro}</Alerta>

      {certificado && (
        <Cartao>
          <p className="mb-4 rounded-lg bg-emerald-50 p-3 text-sm font-medium text-emerald-800 ring-1 ring-emerald-200">
            Certificado autêntico, emitido por este sistema.
          </p>

          <dl className="space-y-3 text-sm">
            <div>
              <dt className="font-medium text-slate-500">Participante</dt>
              <dd className="text-base font-semibold text-slate-900">
                {certificado.nomeParticipante}
              </dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Evento</dt>
              <dd className="text-slate-800">{certificado.tituloEvento}</dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Período</dt>
              <dd className="text-slate-800">
                {periodoDoEvento(certificado.dataInicio, certificado.dataFim)}
              </dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Carga horária</dt>
              <dd className="text-slate-800">{certificado.cargaHoraria} hora(s)</dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Emitido em</dt>
              <dd className="text-slate-800">{formatarData(certificado.dataEmissao)}</dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">Código</dt>
              <dd className="font-mono text-xs text-slate-700">
                {certificado.codigoAutenticidade}
              </dd>
            </div>
          </dl>
        </Cartao>
      )}
    </div>
  )
}
