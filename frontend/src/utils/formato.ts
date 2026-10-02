import type { DataHoraIso } from '../types/api'

const DATA_HORA = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'short',
})

const DATA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' })

export function formatarDataHora(iso: DataHoraIso | undefined): string {
  return iso ? DATA_HORA.format(new Date(iso)) : '—'
}

export function formatarData(iso: DataHoraIso | undefined): string {
  return iso ? DATA.format(new Date(iso)) : '—'
}

/** Converte o valor de um `<input type="datetime-local">` para o ISO que a API espera. */
export function paraIsoLocal(valor: string): DataHoraIso {
  return `${valor}:00`
}

/** Converte o ISO da API para o formato aceito por `<input type="datetime-local">`. */
export function paraCampoDataHora(iso: DataHoraIso | undefined): string {
  return iso ? iso.slice(0, 16) : ''
}

export function periodoDoEvento(dataInicio: DataHoraIso, dataFim: DataHoraIso): string {
  return `${formatarDataHora(dataInicio)} até ${formatarDataHora(dataFim)}`
}
