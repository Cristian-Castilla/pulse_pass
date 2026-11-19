import { it, expect } from 'vitest'
import { TicketSchema } from '../funcionalidades/tickets/tipos'
import { ticket } from './mocks/handlers'

it('rechaza un backend antiguo que todavía no devuelve el campo del titular', () => {
  const antiguo = { ...ticket, userFullName: undefined }
  expect(TicketSchema.safeParse(antiguo).success).toBe(false)
})

it('acepta un titular nulo de un usuario legado, sin fabricar un nombre', () => {
  expect(TicketSchema.parse({ ...ticket, userFullName: null })).toHaveProperty(
    'userFullName',
    null,
  )
})
