import { http } from '../../lib/http'
import { AuthSchema, UsuarioAutenticadoSchema } from './tipos'
import type { Login, Registro } from './tipos'
import { UsuarioSchema } from '../usuarios/tipos'
export const authApi = {
  login: (datos: Login) =>
    http('/auth/login', AuthSchema, {
      method: 'POST',
      body: datos,
      publica: true,
    }),
  me: (signal?: AbortSignal) =>
    http('/auth/me', UsuarioAutenticadoSchema, { signal }),
  registrar: (datos: Registro) =>
    http('/users', UsuarioSchema, {
      method: 'POST',
      body: datos,
      publica: true,
    }),
}
