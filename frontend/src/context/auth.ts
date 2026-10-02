import { createContext, useContext } from 'react'
import type { RegistrarRequest, UsuarioResponse } from '../types/api'

export interface Autenticacao {
  usuario: UsuarioResponse | null
  autenticado: boolean
  ehOrganizador: boolean
  ehParticipante: boolean
  entrar: (email: string, senha: string) => Promise<UsuarioResponse>
  registrar: (dados: RegistrarRequest) => Promise<UsuarioResponse>
  sair: () => void
}

export const AuthContext = createContext<Autenticacao | null>(null)

export function useAuth(): Autenticacao {
  const contexto = useContext(AuthContext)
  if (!contexto) throw new Error('useAuth precisa estar dentro de AuthProvider')
  return contexto
}
