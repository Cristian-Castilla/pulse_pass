import type { ReactNode } from 'react'
import { Cargando, EstadoError } from './ui'
import { Navigate } from 'react-router'
import { ApiError } from '../lib/http'
import { RUTAS } from '../lib/rutas'
interface ConsultaProps<T> {
  consulta: {
    isPending: boolean
    isError: boolean
    error: unknown
    data: T | undefined
    refetch: () => unknown
  }
  children: (data: T) => ReactNode
}
export function Consulta<T>({ consulta, children }: ConsultaProps<T>) {
  if (consulta.isPending) return <Cargando />
  if (consulta.error instanceof ApiError && consulta.error.status === 403)
    return <Navigate to={RUTAS.sinPermisos} replace />
  if (consulta.isError)
    return (
      <EstadoError
        error={consulta.error}
        onReintentar={() => void consulta.refetch()}
      />
    )
  if (consulta.data === undefined) return null
  return children(consulta.data)
}
