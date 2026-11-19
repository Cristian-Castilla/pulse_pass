import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router'
import { configurarSesion } from '../../lib/http'
import { RUTAS } from '../../lib/rutas'
import { AuthContext } from './context'
import { authApi } from './api'
import { SesionGuardadaSchema } from './tipos'
import type { Login, Sesion } from './tipos'
const CLAVE = 'pulsepass.sesion'
function leerGuardada() {
  try {
    const raw = sessionStorage.getItem(CLAVE)
    const result = SesionGuardadaSchema.safeParse(raw ? JSON.parse(raw) : null)
    if (result.success && result.data.expiraEn > Date.now()) return result.data
    sessionStorage.removeItem(CLAVE)
  } catch {
    try {
      sessionStorage.removeItem(CLAVE)
    } catch {
      /* Almacenamiento restringido: sesión solo en memoria. */
    }
  }
  return null
}
export function AuthProvider({ children }: { children: ReactNode }) {
  const [guardada] = useState(leerGuardada)
  const [sesion, setSesion] = useState<Sesion>(
    guardada ? { estado: 'cargando' } : { estado: 'anonimo' },
  )
  const token = useRef<string | null>(guardada?.token ?? null)
  const revision = useRef(0)
  const navegar = useNavigate()
  const location = useLocation()
  const ruta = useRef(location.pathname + location.search)
  const client = useQueryClient()
  useLayoutEffect(() => {
    ruta.current = location.pathname + location.search
  }, [location.pathname, location.search])
  const limpiar = useCallback(
    (expirada = false) => {
      revision.current++
      token.current = null
      try {
        sessionStorage.removeItem(CLAVE)
        sessionStorage.removeItem('pulsepass.creados')
      } catch {
        /* No persistir no impide cerrar sesión. */
      }
      void client.cancelQueries()
      client.clear()
      setSesion({
        estado: 'anonimo',
        motivo: expirada ? 'expirada' : 'salida',
        desde: ruta.current,
      })
      void navegar(expirada ? RUTAS.login : RUTAS.inicio, {
        replace: true,
        state: expirada ? { motivo: 'expirada', desde: ruta.current } : null,
      })
    },
    [client, navegar],
  )
  useLayoutEffect(() => {
    configurarSesion(
      () => token.current,
      () => limpiar(true),
    )
    return () =>
      configurarSesion(
        () => null,
        () => {},
      )
  }, [limpiar])
  useEffect(() => {
    if (!guardada) return
    const controller = new AbortController()
    const actual = revision.current
    authApi
      .me(controller.signal)
      .then((usuario) => {
        if (actual === revision.current)
          setSesion({ estado: 'autenticado', ...guardada, usuario })
      })
      .catch(() => {
        if (!controller.signal.aborted && actual === revision.current)
          limpiar(true)
      })
    return () => controller.abort()
  }, [guardada, limpiar])
  useEffect(() => {
    if (sesion.estado !== 'autenticado') return
    const timer = window.setTimeout(
      () => limpiar(true),
      Math.max(0, sesion.expiraEn - Date.now()),
    )
    return () => window.clearTimeout(timer)
  }, [sesion, limpiar])
  const iniciarSesion = async (datos: Login) => {
    const actual = ++revision.current
    const auth = await authApi.login(datos)
    if (actual !== revision.current) return
    const siguiente = {
      token: auth.accessToken,
      expiraEn: Date.now() + auth.expiresIn * 1000,
    }
    token.current = siguiente.token
    try {
      const usuario = await authApi.me()
      if (actual !== revision.current) return
      client.clear()
      try {
        sessionStorage.setItem(CLAVE, JSON.stringify(siguiente))
      } catch {
        /* La sesión continúa solo en memoria si el navegador bloquea storage. */
      }
      setSesion({ estado: 'autenticado', ...siguiente, usuario })
    } catch (error) {
      if (actual === revision.current) {
        token.current = null
        setSesion({ estado: 'anonimo' })
      }
      throw error
    }
  }
  return (
    <AuthContext
      value={{
        sesion,
        iniciarSesion,
        cerrarSesion: () => limpiar(),
        tieneRol: (...roles) =>
          sesion.estado === 'autenticado' &&
          roles.some((r) => sesion.usuario.roles.includes(r)),
      }}
    >
      {children}
    </AuthContext>
  )
}
