import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { api, sessao } from '../api/client'
import type { LoginResponse, RegistrarRequest, UsuarioResponse } from '../types/api'

interface Autenticacao {
  usuario: UsuarioResponse | null
  autenticado: boolean
  ehOrganizador: boolean
  ehParticipante: boolean
  entrar: (email: string, senha: string) => Promise<UsuarioResponse>
  registrar: (dados: RegistrarRequest) => Promise<UsuarioResponse>
  sair: () => void
}

const AuthContext = createContext<Autenticacao | null>(null)

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

export function useAuth(): Autenticacao {
  const contexto = useContext(AuthContext)
  if (!contexto) throw new Error('useAuth precisa estar dentro de AuthProvider')
  return contexto
}
