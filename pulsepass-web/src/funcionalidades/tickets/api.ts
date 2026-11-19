import { z } from 'zod'
import { http } from '../../lib/http'
import { TicketSchema } from './tipos'
import type { z as Z } from 'zod'
import type { TipoTicketSchema } from './tipos'
export const ticketsApi = {
  ticket: (code: string, signal?: AbortSignal) =>
    http(`/tickets/${encodeURIComponent(code)}`, TicketSchema, { signal }),
  porUsuario: (email: string, signal?: AbortSignal) =>
    http(
      `/tickets/by-user?${new URLSearchParams({ email })}`,
      z.array(TicketSchema),
      { signal },
    ),
  pagados: (code: string, signal?: AbortSignal) =>
    http(
      `/events/${encodeURIComponent(code)}/tickets/paid`,
      z.array(TicketSchema),
      { signal },
    ),
  comprar: (
    userEmail: string,
    eventCode: string,
    type: Z.infer<typeof TipoTicketSchema>,
  ) =>
    http('/tickets', TicketSchema, {
      method: 'POST',
      body: { userEmail, eventCode, type },
    }),
  cancelar: (code: string) =>
    http(`/tickets/${encodeURIComponent(code)}/cancel`, TicketSchema, {
      method: 'PATCH',
    }),
  usar: (code: string) =>
    http(`/tickets/${encodeURIComponent(code)}/use`, TicketSchema, {
      method: 'PATCH',
    }),
}
