import { z } from 'zod'
import { http } from '../../lib/http'
import { ArtistaSchema } from './tipos'
export const artistasApi = {
  activos: (signal?: AbortSignal) =>
    http('/artists/active', z.array(ArtistaSchema), { signal }),
  artista: (id: number, signal?: AbortSignal) =>
    http(`/artists/${id}`, ArtistaSchema, { signal }),
  porNombre: (stageName: string, signal?: AbortSignal) =>
    http(
      `/artists/by-stage-name?${new URLSearchParams({ stageName })}`,
      ArtistaSchema,
      { signal },
    ),
}
