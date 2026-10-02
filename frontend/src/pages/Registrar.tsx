import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { Alerta, Botao, Campo, Cartao, Entrada, Selecao } from '../components/ui'
import type { RegistrarRequest } from '../types/api'

export default function Registrar() {
  const { registrar } = useAuth()
  const navegar = useNavigate()

  const [dados, setDados] = useState<Required<RegistrarRequest>>({
    nome: '',
    email: '',
    senha: '',
    papel: 'PARTICIPANTE',
  })
  const [erro, setErro] = useState<string | null>(null)
  const [errosPorCampo, setErrosPorCampo] = useState<Record<string, string>>({})
  const [enviando, setEnviando] = useState(false)

  function alterar(campo: keyof RegistrarRequest) {
    return (evento: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setDados((anterior) => ({ ...anterior, [campo]: evento.target.value }))
  }

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErro(null)
    setErrosPorCampo({})
    setEnviando(true)
    try {
      await registrar(dados)
      navegar('/', { replace: true })
    } catch (falha) {
      if (falha instanceof ApiError) {
        setErrosPorCampo(falha.errosPorCampo)
        setErro(falha.campos.length ? null : falha.message)
      } else {
        setErro('Não foi possível criar a conta agora.')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Criar conta</h1>

      <Cartao>
        <form onSubmit={enviar} className="space-y-4">
          <Campo rotulo="Nome" erro={errosPorCampo.nome}>
            <Entrada
              value={dados.nome}
              onChange={alterar('nome')}
              erro={errosPorCampo.nome}
              autoComplete="name"
              required
            />
          </Campo>

          <Campo rotulo="E-mail" erro={errosPorCampo.email}>
            <Entrada
              type="email"
              value={dados.email}
              onChange={alterar('email')}
              erro={errosPorCampo.email}
              autoComplete="email"
              required
            />
          </Campo>

          <Campo
            rotulo="Senha"
            erro={errosPorCampo.senha}
            dica="No mínimo 8 caracteres."
          >
            <Entrada
              type="password"
              value={dados.senha}
              onChange={alterar('senha')}
              erro={errosPorCampo.senha}
              autoComplete="new-password"
              minLength={8}
              required
            />
          </Campo>

          <Campo
            rotulo="Como você vai usar o sistema"
            dica="Organizador cria e gerencia eventos; participante se inscreve neles."
          >
            <Selecao value={dados.papel} onChange={alterar('papel')}>
              <option value="PARTICIPANTE">Quero me inscrever em eventos</option>
              <option value="ORGANIZADOR">Quero organizar eventos</option>
            </Selecao>
          </Campo>

          <Alerta>{erro}</Alerta>

          <Botao type="submit" disabled={enviando} className="w-full">
            {enviando ? 'Criando conta…' : 'Criar conta'}
          </Botao>
        </form>
      </Cartao>

      <p className="mt-4 text-center text-sm text-slate-600">
        Já tem conta?{' '}
        <Link to="/entrar" className="font-medium text-marinho-700 hover:underline">
          Entrar
        </Link>
      </p>
    </div>
  )
}
