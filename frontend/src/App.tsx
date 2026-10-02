import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import RotaProtegida from './components/RotaProtegida'
import Catalogo from './pages/Catalogo'
import DetalheEvento from './pages/DetalheEvento'
import Entrar from './pages/Entrar'
import FormularioEvento from './pages/FormularioEvento'
import GerenciarEvento from './pages/GerenciarEvento'
import MeusEventos from './pages/MeusEventos'
import MinhasInscricoes from './pages/MinhasInscricoes'
import NaoEncontrada from './pages/NaoEncontrada'
import Registrar from './pages/Registrar'
import ValidarCertificado from './pages/ValidarCertificado'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* Público */}
        <Route index element={<Catalogo />} />
        <Route path="eventos/:id" element={<DetalheEvento />} />
        <Route path="certificados/validar" element={<ValidarCertificado />} />
        <Route path="certificados/validar/:codigo" element={<ValidarCertificado />} />
        <Route path="entrar" element={<Entrar />} />
        <Route path="registrar" element={<Registrar />} />

        {/* Participante */}
        <Route
          path="minhas-inscricoes"
          element={
            <RotaProtegida papel="PARTICIPANTE">
              <MinhasInscricoes />
            </RotaProtegida>
          }
        />

        {/* Organizador */}
        <Route
          path="organizador"
          element={
            <RotaProtegida papel="ORGANIZADOR">
              <MeusEventos />
            </RotaProtegida>
          }
        />
        <Route
          path="organizador/eventos/novo"
          element={
            <RotaProtegida papel="ORGANIZADOR">
              <FormularioEvento />
            </RotaProtegida>
          }
        />
        <Route
          path="organizador/eventos/:id"
          element={
            <RotaProtegida papel="ORGANIZADOR">
              <GerenciarEvento />
            </RotaProtegida>
          }
        />
        <Route
          path="organizador/eventos/:id/editar"
          element={
            <RotaProtegida papel="ORGANIZADOR">
              <FormularioEvento />
            </RotaProtegida>
          }
        />

        <Route path="*" element={<NaoEncontrada />} />
      </Route>
    </Routes>
  )
}
