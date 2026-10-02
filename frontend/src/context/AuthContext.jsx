import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { api, sessao } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => sessao.usuario())

  const entrar = useCallback(async (email, senha) => {
    const { token, usuario: autenticado } = await api.post('/api/auth/login', { email, senha })
    sessao.salvar(token, autenticado)
    setUsuario(autenticado)
    return autenticado
  }, [])

  const registrar = useCallback(async (dados) => {
    await api.post('/api/auth/registrar', dados)
    return entrar(dados.email, dados.senha)
  }, [entrar])

  const sair = useCallback(() => {
    sessao.limpar()
    setUsuario(null)
  }, [])

  const valor = useMemo(() => ({
    usuario,
    autenticado: Boolean(usuario),
    ehOrganizador: Boolean(usuario?.papeis?.includes('ORGANIZADOR')),
    ehParticipante: Boolean(usuario?.papeis?.includes('PARTICIPANTE')),
    entrar,
    registrar,
    sair,
  }), [usuario, entrar, registrar, sair])

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const contexto = useContext(AuthContext)
  if (!contexto) throw new Error('useAuth precisa estar dentro de AuthProvider')
  return contexto
}
