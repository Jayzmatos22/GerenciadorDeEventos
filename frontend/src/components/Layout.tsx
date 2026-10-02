import type { ReactNode } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Botao } from './ui'

function ItemMenu({ to, children }: { to: string; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `rounded-lg px-3 py-2 text-sm font-medium transition ${
          isActive ? 'bg-marinho-50 text-marinho-700' : 'text-slate-600 hover:text-marinho-700'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

export default function Layout() {
  const { usuario, ehOrganizador, ehParticipante, sair } = useAuth()
  const navegar = useNavigate()

  function encerrarSessao() {
    sair()
    navegar('/')
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
          <Link to="/" className="mr-auto text-base font-semibold text-marinho-700">
            Eventos Acadêmicos
            <span className="ml-2 text-xs font-normal text-slate-500">UNISA</span>
          </Link>

          <nav className="flex flex-wrap items-center gap-1">
            <ItemMenu to="/">Eventos</ItemMenu>
            <ItemMenu to="/certificados/validar">Validar certificado</ItemMenu>
            {ehParticipante && <ItemMenu to="/minhas-inscricoes">Minhas inscrições</ItemMenu>}
            {ehOrganizador && <ItemMenu to="/organizador">Meus eventos</ItemMenu>}
          </nav>

          {usuario ? (
            <div className="flex items-center gap-3">
              <span className="hidden text-sm text-slate-600 sm:inline">{usuario.nome}</span>
              <Botao variante="discreto" onClick={encerrarSessao}>
                Sair
              </Botao>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link
                to="/entrar"
                className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:text-marinho-700"
              >
                Entrar
              </Link>
              <Link
                to="/registrar"
                className="rounded-lg bg-marinho-600 px-4 py-2 text-sm font-medium text-white hover:bg-marinho-700"
              >
                Criar conta
              </Link>
            </div>
          )}
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white py-6">
        <p className="mx-auto max-w-6xl px-4 text-xs text-slate-500">
          Gerenciador de Eventos Acadêmicos — trabalho acadêmico, UNISA.
        </p>
      </footer>
    </div>
  )
}
