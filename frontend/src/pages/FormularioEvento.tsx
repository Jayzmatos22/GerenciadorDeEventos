import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import {
  Alerta,
  AreaTexto,
  Botao,
  Campo,
  Cartao,
  Carregando,
  Entrada,
} from '../components/ui'
import { paraCampoDataHora, paraIsoLocal } from '../utils/formato'
import type {
  CampoExtraido,
  EventoExtraidoDTO,
  EventoRequest,
  EventoResponse,
} from '../types/api'

/** Espelha o EventoRequest, mas tudo como texto: é o que os `<input>` carregam. */
type FormularioEventoDados = Record<CampoExtraido, string>

const FORMULARIO_VAZIO: FormularioEventoDados = {
  titulo: '',
  descricao: '',
  local: '',
  dataInicio: '',
  dataFim: '',
  cargaHoraria: '',
  limiteVagas: '',
}

const ROTULOS: Record<CampoExtraido, string> = {
  titulo: 'título',
  descricao: 'descrição',
  local: 'local',
  dataInicio: 'data de início',
  dataFim: 'data de fim',
  cargaHoraria: 'carga horária',
  limiteVagas: 'limite de vagas',
}

/**
 * Painel do cadastro assistido por IA (RF-03). A extração nunca persiste nada: ela só
 * preenche o formulário, e o organizador confirma. Quando a IA está fora do ar o painel
 * se recolhe e o formulário manual continua ali, intocado (RN-10).
 */
function AssistenteIA({ onExtraido }: { onExtraido: (extraido: EventoExtraidoDTO) => void }) {
  const [texto, setTexto] = useState('')
  const [interpretando, setInterpretando] = useState(false)
  const [indisponivel, setIndisponivel] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function interpretar() {
    setErro(null)
    setInterpretando(true)
    try {
      const extraido = await api.post<EventoExtraidoDTO>('/api/eventos/interpretar', { texto })
      onExtraido(extraido)
    } catch (falha) {
      if (falha instanceof ApiError && falha.codigo === 'IA_INDISPONIVEL') {
        setIndisponivel(true)
      } else {
        setErro(
          falha instanceof ApiError
            ? falha.message
            : 'Não foi possível interpretar o texto agora.',
        )
      }
    } finally {
      setInterpretando(false)
    }
  }

  if (indisponivel) {
    return (
      <Alerta tom="aviso" titulo="Assistente de IA indisponível">
        Siga preenchendo o formulário abaixo normalmente — nada do seu trabalho foi perdido.
      </Alerta>
    )
  }

  return (
    <Cartao className="bg-marinho-50/60">
      <h2 className="font-semibold text-marinho-900">Preencher a partir de um texto</h2>
      <p className="mt-1 mb-3 text-sm text-marinho-900/80">
        Cole o convite, o e-mail ou a chamada do evento. A IA extrai os campos e você revisa
        antes de salvar — nada é publicado automaticamente.
      </p>

      <Campo rotulo="Texto do evento">
        <AreaTexto
          rows={5}
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder="Ex.: A Semana de Tecnologia acontece no auditório da UNISA em 10 de novembro de 2026, das 19h às 22h, com 120 vagas."
        />
      </Campo>

      <div className="mt-3">
        <Alerta>{erro}</Alerta>
      </div>

      <Botao
        type="button"
        onClick={interpretar}
        disabled={interpretando || texto.trim().length === 0}
        className="mt-3"
      >
        {interpretando ? 'Interpretando…' : 'Interpretar texto'}
      </Botao>
    </Cartao>
  )
}

export default function FormularioEvento() {
  const { id } = useParams()
  const navegar = useNavigate()
  const editando = Boolean(id)

  const [formulario, setFormulario] = useState<FormularioEventoDados>(FORMULARIO_VAZIO)
  const [camposSugeridos, setCamposSugeridos] = useState<CampoExtraido[]>([])
  const [camposNaoIdentificados, setCamposNaoIdentificados] = useState<CampoExtraido[]>([])
  const [errosPorCampo, setErrosPorCampo] = useState<Record<string, string>>({})
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(editando)
  const [salvando, setSalvando] = useState(false)

  useEffect(() => {
    if (!editando) return

    let cancelado = false
    api
      .get<EventoResponse>(`/api/eventos/${id}`)
      .then((evento) => {
        if (cancelado) return
        setFormulario({
          titulo: evento.titulo,
          descricao: evento.descricao,
          local: evento.local,
          dataInicio: paraCampoDataHora(evento.dataInicio),
          dataFim: paraCampoDataHora(evento.dataFim),
          cargaHoraria: String(evento.cargaHoraria),
          limiteVagas: String(evento.limiteVagas),
        })
      })
      .catch(() => {
        if (!cancelado) setErro('Não foi possível carregar o evento para edição.')
      })
      .finally(() => {
        if (!cancelado) setCarregando(false)
      })

    return () => {
      cancelado = true
    }
  }, [editando, id])

  function alterar(campo: CampoExtraido) {
    return (evento: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setFormulario((anterior) => ({ ...anterior, [campo]: evento.target.value }))
      setCamposSugeridos((sugeridos) => sugeridos.filter((nome) => nome !== campo))
    }
  }

  function aplicarExtracao(extraido: EventoExtraidoDTO) {
    const preenchidos: CampoExtraido[] = []

    setFormulario((anterior) => {
      const novo = { ...anterior }
      const atribuir = (campo: CampoExtraido, valor: string | number | undefined) => {
        if (valor === undefined || valor === '') return
        novo[campo] = String(valor)
        preenchidos.push(campo)
      }

      atribuir('titulo', extraido.titulo)
      atribuir('descricao', extraido.descricao)
      atribuir('local', extraido.local)
      atribuir('dataInicio', paraCampoDataHora(extraido.dataInicio))
      atribuir('dataFim', paraCampoDataHora(extraido.dataFim))
      atribuir('cargaHoraria', extraido.cargaHoraria)
      atribuir('limiteVagas', extraido.limiteVagas)
      return novo
    })

    setCamposSugeridos(preenchidos)
    setCamposNaoIdentificados(extraido.camposNaoIdentificados)
  }

  async function salvar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErro(null)
    setErrosPorCampo({})
    setSalvando(true)

    const corpo: EventoRequest = {
      titulo: formulario.titulo,
      descricao: formulario.descricao,
      local: formulario.local,
      dataInicio: paraIsoLocal(formulario.dataInicio),
      dataFim: paraIsoLocal(formulario.dataFim),
      cargaHoraria: Number(formulario.cargaHoraria),
      limiteVagas: Number(formulario.limiteVagas),
    }

    try {
      const salvo = editando
        ? await api.put<EventoResponse>(`/api/eventos/${id}`, corpo)
        : await api.post<EventoResponse>('/api/eventos', corpo)
      navegar(`/organizador/eventos/${salvo.id}`, { replace: true })
    } catch (falha) {
      if (falha instanceof ApiError) {
        setErrosPorCampo(falha.errosPorCampo)
        setErro(falha.campos.length ? null : falha.message)
      } else {
        setErro('Não foi possível salvar o evento agora.')
      }
    } finally {
      setSalvando(false)
    }
  }

  if (carregando) return <Carregando texto="Carregando evento…" />

  const dicaSugerida = (campo: CampoExtraido): string | undefined =>
    camposSugeridos.includes(campo) ? 'Preenchido pela IA — confira antes de salvar.' : undefined

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">
          {editando ? 'Editar evento' : 'Novo evento'}
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          {editando
            ? 'As alterações valem imediatamente para quem já está inscrito.'
            : 'O evento nasce como rascunho. Publique quando estiver pronto para receber inscrições.'}
        </p>
      </header>

      {!editando && <AssistenteIA onExtraido={aplicarExtracao} />}

      {camposNaoIdentificados.length > 0 && (
        <Alerta tom="info" titulo="A IA não encontrou tudo no texto">
          Preencha à mão:{' '}
          {camposNaoIdentificados.map((campo) => ROTULOS[campo] ?? campo).join(', ')}.
        </Alerta>
      )}

      <Cartao>
        <form onSubmit={salvar} className="space-y-4">
          <Campo rotulo="Título" erro={errosPorCampo.titulo} dica={dicaSugerida('titulo')}>
            <Entrada
              value={formulario.titulo}
              onChange={alterar('titulo')}
              erro={errosPorCampo.titulo}
              maxLength={160}
              required
            />
          </Campo>

          <Campo
            rotulo="Descrição"
            erro={errosPorCampo.descricao}
            dica={dicaSugerida('descricao')}
          >
            <AreaTexto
              rows={4}
              value={formulario.descricao}
              onChange={alterar('descricao')}
              erro={errosPorCampo.descricao}
              required
            />
          </Campo>

          <Campo rotulo="Local" erro={errosPorCampo.local} dica={dicaSugerida('local')}>
            <Entrada
              value={formulario.local}
              onChange={alterar('local')}
              erro={errosPorCampo.local}
              maxLength={200}
              required
            />
          </Campo>

          <div className="grid gap-4 sm:grid-cols-2">
            <Campo
              rotulo="Início"
              erro={errosPorCampo.dataInicio}
              dica={dicaSugerida('dataInicio')}
            >
              <Entrada
                type="datetime-local"
                value={formulario.dataInicio}
                onChange={alterar('dataInicio')}
                erro={errosPorCampo.dataInicio}
                required
              />
            </Campo>

            <Campo rotulo="Fim" erro={errosPorCampo.dataFim} dica={dicaSugerida('dataFim')}>
              <Entrada
                type="datetime-local"
                value={formulario.dataFim}
                onChange={alterar('dataFim')}
                erro={errosPorCampo.dataFim}
                required
              />
            </Campo>

            <Campo
              rotulo="Carga horária (horas)"
              erro={errosPorCampo.cargaHoraria}
              dica={dicaSugerida('cargaHoraria')}
            >
              <Entrada
                type="number"
                min={1}
                value={formulario.cargaHoraria}
                onChange={alterar('cargaHoraria')}
                erro={errosPorCampo.cargaHoraria}
                required
              />
            </Campo>

            <Campo
              rotulo="Limite de vagas"
              erro={errosPorCampo.limiteVagas}
              dica={dicaSugerida('limiteVagas')}
            >
              <Entrada
                type="number"
                min={1}
                value={formulario.limiteVagas}
                onChange={alterar('limiteVagas')}
                erro={errosPorCampo.limiteVagas}
                required
              />
            </Campo>
          </div>

          <Alerta>{erro}</Alerta>

          <div className="flex gap-2">
            <Botao type="submit" disabled={salvando}>
              {salvando ? 'Salvando…' : editando ? 'Salvar alterações' : 'Criar rascunho'}
            </Botao>
            <Botao type="button" variante="discreto" onClick={() => navegar(-1)}>
              Cancelar
            </Botao>
          </div>
        </form>
      </Cartao>
    </div>
  )
}
