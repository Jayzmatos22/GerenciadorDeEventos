import { BotaoLink } from '../components/ui'

export default function NaoEncontrada() {
  return (
    <div className="py-16 text-center">
      <p className="text-sm font-semibold text-marinho-600">404</p>
      <h1 className="mt-2 text-2xl font-semibold text-slate-900">Página não encontrada</h1>
      <p className="mt-2 text-sm text-slate-600">
        O endereço que você abriu não existe neste sistema.
      </p>
      <BotaoLink to="/" className="mt-6">
        Ver eventos abertos
      </BotaoLink>
    </div>
  )
}
