import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import type { EstadoDeRota } from '../types/rota'
import { ApiError } from '../api/client'
import { useAuth } from '../context/auth'
import { Alerta, Botao, Campo, Cartao, Entrada } from '../components/ui'

export default function Entrar() {
  const { entrar } = useAuth()
  const navegar = useNavigate()
  const localizacao = useLocation()

  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErro(null)
    setEnviando(true)
    try {
      await entrar(email, senha)
      const origem = (localizacao.state as EstadoDeRota | null)?.de
      void navegar(origem ?? '/', { replace: true })
    } catch (falha) {
      setErro(falha instanceof ApiError ? falha.message : 'Não foi possível entrar agora.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Entrar</h1>

      <Cartao>
        <form
          onSubmit={(evento) => {
            void enviar(evento)
          }}
          className="space-y-4"
        >
          <Campo rotulo="E-mail">
            <Entrada
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </Campo>

          <Campo rotulo="Senha">
            <Entrada
              type="password"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              autoComplete="current-password"
              required
            />
          </Campo>

          <Alerta>{erro}</Alerta>

          <Botao type="submit" disabled={enviando} className="w-full">
            {enviando ? 'Entrando…' : 'Entrar'}
          </Botao>
        </form>
      </Cartao>

      <p className="mt-4 text-center text-sm text-slate-600">
        Ainda não tem conta?{' '}
        <Link to="/registrar" className="font-medium text-marinho-700 hover:underline">
          Criar conta
        </Link>
      </p>
    </div>
  )
}
