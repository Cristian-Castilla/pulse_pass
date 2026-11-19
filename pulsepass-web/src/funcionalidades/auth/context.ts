import { createContext } from 'react'
import type { Login, Rol, Sesion } from './tipos'
export interface AuthValor {
  sesion: Sesion
  iniciarSesion: (datos: Login) => Promise<void>
  cerrarSesion: () => void
  tieneRol: (...roles: Rol[]) => boolean
}
export const AuthContext = createContext<AuthValor | null>(null)
