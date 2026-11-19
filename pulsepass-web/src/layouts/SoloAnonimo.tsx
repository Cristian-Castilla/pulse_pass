import { Navigate, Outlet, useLocation } from 'react-router'
import { z } from 'zod'
import { destinoSeguro } from '../lib/parametros'
import { useAuth } from '../funcionalidades/auth/hooks'

import { Cargando } from '../componentes/ui'
export default function SoloAnonimo() {
  const { sesion } = useAuth()
  const location = useLocation()
  const state = z
    .object({ desde: z.unknown().optional() })
    .safeParse(location.state)
  return sesion.estado === 'cargando' ? (
    <Cargando />
  ) : sesion.estado === 'autenticado' ? (
    <Navigate
      to={destinoSeguro(state.success ? state.data.desde : null)}
      replace
    />
  ) : (
    <Outlet />
  )
}
