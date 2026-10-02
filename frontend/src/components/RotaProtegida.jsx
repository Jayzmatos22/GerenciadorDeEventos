import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Alerta } from './ui'

/**
 * Exige sessão e, opcionalmente, um papel. Sem sessão manda para o login guardando a rota
 * de origem; com sessão e sem o papel, explica em vez de redirecionar em silêncio.
 */
export default function RotaProtegida({ papel, children }) {
  const { autenticado, usuario } = useAuth()
  const localizacao = useLocation()

  if (!autenticado) {
    return <Navigate to="/entrar" replace state={{ de: localizacao.pathname }} />
  }

  if (papel && !usuario.papeis.includes(papel)) {
    return (
      <Alerta tom="aviso" titulo="Esta área não é do seu perfil">
        Ela é restrita a quem tem o papel {papel.toLowerCase()}.
      </Alerta>
    )
  }

  return children
}
