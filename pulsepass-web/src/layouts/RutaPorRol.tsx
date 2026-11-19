import { Navigate, Outlet } from 'react-router'
import { useAuth } from '../funcionalidades/auth/hooks'
import type { Rol } from '../funcionalidades/auth/tipos'
import { RUTAS } from '../lib/rutas'
export default function RutaPorRol({ roles }: { roles: Rol[] }) {
  const { tieneRol } = useAuth()
  return tieneRol(...roles) ? (
    <Outlet />
  ) : (
    <Navigate to={RUTAS.sinPermisos} replace />
  )
}
