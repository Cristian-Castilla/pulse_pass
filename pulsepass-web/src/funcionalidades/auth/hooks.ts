import { useContext } from 'react'
import { AuthContext } from './context'
export function useAuth() {
  const contexto = useContext(AuthContext)
  if (!contexto) throw new Error('AuthProvider requerido')
  return contexto
}
