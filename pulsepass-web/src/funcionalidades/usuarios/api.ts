import { http } from '../../lib/http'
import { UsuarioSchema } from './tipos'
export const usuariosApi = {
  porEmail: (email: string, signal?: AbortSignal) =>
    http(`/users/by-email?${new URLSearchParams({ email })}`, UsuarioSchema, {
      signal,
    }),
  porUsername: (username: string, signal?: AbortSignal) =>
    http(
      `/users/by-username?${new URLSearchParams({ username })}`,
      UsuarioSchema,
      { signal },
    ),
}
