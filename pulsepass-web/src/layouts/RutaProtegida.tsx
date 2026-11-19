import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../funcionalidades/auth/hooks'
import { RUTAS } from '../lib/rutas'
import { Cargando } from '../componentes/ui'
export default function RutaProtegida() {
  const { sesion } = useAuth()
  const location = useLocation()
  if (sesion.estado === 'cargando') return <Cargando />
  if (sesion.estado === 'anonimo')
    return (
      <Navigate
        to={sesion.motivo === 'salida' ? RUTAS.inicio : RUTAS.login}
        replace
        state={{
          desde: sesion.desde ?? location.pathname + location.search,
          motivo: sesion.motivo,
        }}
      />
    )
  return <Outlet />
}
