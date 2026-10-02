const DATA_HORA = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'short',
})

const DATA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' })

export function formatarDataHora(iso) {
  return iso ? DATA_HORA.format(new Date(iso)) : '—'
}

export function formatarData(iso) {
  return iso ? DATA.format(new Date(iso)) : '—'
}

/** Converte o valor de um <input type="datetime-local"> para o ISO que a API espera. */
export function paraIsoLocal(valor) {
  return valor ? `${valor}:00` : null
}

/** Converte o ISO da API para o formato aceito por <input type="datetime-local">. */
export function paraCampoDataHora(iso) {
  return iso ? iso.slice(0, 16) : ''
}

export function periodoDoEvento(dataInicio, dataFim) {
  const inicio = formatarDataHora(dataInicio)
  const fim = formatarDataHora(dataFim)
  return `${inicio} até ${fim}`
}
