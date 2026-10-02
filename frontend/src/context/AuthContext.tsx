import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { api, sessao } from '../api/client'
import type { LoginResponse, RegistrarRequest, UsuarioResponse } from '../types/api'
import { AuthContext, type Autenticacao } from './auth'

/**
 * O contexto e o hook `useAuth` vivem em `auth.ts`, não aqui. O Vite só consegue atualizar um
 * módulo em memória se ele exportar apenas componentes; exportar um hook junto derrubaria o
 * estado da aplicação a cada salvamento durante o desenvolvimento.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioResponse | null>(() => sessao.usuario())

  const entrar = useCallback(async (email: string, senha: string) => {
    const { token, usuario: autenticado } = await api.post<LoginResponse>('/api/auth/login', {
      email,
      senha,
    })
    sessao.salvar(token, autenticado)
    setUsuario(autenticado)
    return autenticado
  }, [])

  const registrar = useCallback(
    async (dados: RegistrarRequest) => {
      await api.post<UsuarioResponse>('/api/auth/registrar', dados)
      return entrar(dados.email, dados.senha)
    },
    [entrar],
  )

  const sair = useCallback(() => {
    sessao.limpar()
    setUsuario(null)
  }, [])

  const valor = useMemo<Autenticacao>(
    () => ({
      usuario,
      autenticado: usuario !== null,
      ehOrganizador: usuario?.papeis.includes('ORGANIZADOR') ?? false,
      ehParticipante: usuario?.papeis.includes('PARTICIPANTE') ?? false,
      entrar,
      registrar,
      sair,
    }),
    [usuario, entrar, registrar, sair],
  )

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}
