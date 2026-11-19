import { z } from 'zod'
import { http } from '../../lib/http'
import { EventoSchema, EventoResumenSchema } from './tipos'
import type { CrearEvento } from './tipos'
export const eventosApi = {
  publicados: (signal?: AbortSignal) =>
    http('/events/published', z.array(EventoResumenSchema), { signal }),
  evento: (c: string, signal?: AbortSignal) =>
    http(`/events/${encodeURIComponent(c)}`, EventoSchema, { signal }),
  porArtista: (nombre: string, signal?: AbortSignal) =>
    http(
      `/events/by-artist?${new URLSearchParams({ stageName: nombre })}`,
      z.array(EventoResumenSchema),
      { signal },
    ),
  crear: (datos: CrearEvento) =>
    http('/events', EventoSchema, { method: 'POST', body: datos }),
  publicar: (c: string) =>
    http(`/events/${encodeURIComponent(c)}/publish`, EventoSchema, {
      method: 'PATCH',
    }),
  asociar: (c: string, id: number) =>
    http(`/events/${encodeURIComponent(c)}/artists/${id}`, EventoSchema, {
      method: 'POST',
    }),
}
