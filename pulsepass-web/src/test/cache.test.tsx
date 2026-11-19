import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { crearQueryClient } from '../lib/queryClient'
import {
  useCancelarTicket,
  useUsarTicket,
} from '../funcionalidades/tickets/hooks'
import { server } from './mocks/server'
import { ticket } from './mocks/handlers'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it.each([
  ['cancelar', useCancelarTicket],
  ['usar', useUsarTicket],
] as const)(
  '%s invalida lista propia, detalle y tickets pagados',
  async (_nombre, hook) => {
    const client = crearQueryClient()
    const keys = [
      ['ticket', ticket.ticketCode],
      ['tickets', 'usuario', ticket.userEmail],
      ['tickets', 'pagados', ticket.eventCode],
    ]
    for (const key of keys) client.setQueryData(key, ticket)
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(hook, { wrapper })
    await act(async () => {
      await result.current.mutateAsync(ticket.ticketCode)
    })
    for (const key of keys)
      expect(client.getQueryState(key)?.isInvalidated).toBe(true)
    client.clear()
  },
)
