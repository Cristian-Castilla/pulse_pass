import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { ticket, evento, error } from './mocks/handlers'
import { renderRuta } from './utils'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
async function comprar() {
  await userEvent.click(
    await screen.findByRole('button', { name: 'Comprar entrada' }),
  )
  await userEvent.click(
    screen.getByRole('button', { name: 'Confirmar compra' }),
  )
}
it('TEST-FE-TKT-001 compra 201 navega a confirmación', async () => {
  const { router } = renderRuta('/eventos/CMF-2026', true)
  await comprar()
  await waitFor(() =>
    expect(router.state.location.pathname).toBe(
      '/compra/confirmacion/TKT-8F2A1C',
    ),
  )
  expect(
    await screen.findByRole('heading', {
      name: '¡Compra realizada con éxito!',
    }),
  ).toBeInTheDocument()
})
it('TEST-FE-TKT-002 anónimo va a login', async () => {
  renderRuta('/eventos/CMF-2026')
  await userEvent.click(
    await screen.findByRole('button', { name: 'Inicia sesión para comprar' }),
  )
  expect(
    await screen.findByRole('heading', { name: 'Inicia sesión' }),
  ).toBeInTheDocument()
})
it('TEST-FE-TKT-003 409 de compra conserva modal', async () => {
  server.use(
    http.post('/api/tickets', () => error(409, 'No cumple la edad mínima')),
  )
  renderRuta('/eventos/CMF-2026', true)
  await comprar()
  expect(
    await screen.findByText('No cumple la edad mínima'),
  ).toBeInTheDocument()
  expect(screen.getByRole('dialog')).toBeInTheDocument()
})
it('TEST-FE-TKT-004 compra usa solo correo propio y no envía precio', async () => {
  let body: unknown
  server.use(
    http.post('/api/tickets', async ({ request }) => {
      body = await request.json()
      return HttpResponse.json(ticket, { status: 201 })
    }),
  )
  renderRuta('/eventos/CMF-2026', true)
  await comprar()
  await waitFor(() =>
    expect(body).toEqual({
      userEmail: 'andrea@pulsepass.test',
      eventCode: 'CMF-2026',
      type: 'GENERAL',
    }),
  )
})
it('TEST-FE-TKT-005 lista y filtro por estado', async () => {
  renderRuta('/mis-tickets', true)
  expect(await screen.findByText('TKT-8F2A1C')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Usados' }))
  expect(
    await screen.findByText('Sin tickets con este estado'),
  ).toBeInTheDocument()
})
it('TEST-FE-TKT-006 lista vacía', async () => {
  server.use(http.get('/api/tickets/by-user', () => HttpResponse.json([])))
  renderRuta('/mis-tickets', true)
  expect(await screen.findByText('Aún no tienes tickets')).toBeInTheDocument()
})
it('TEST-FE-TKT-007 cancelar actualiza lista', async () => {
  let cancelado = false
  server.use(
    http.get('/api/tickets/by-user', () =>
      HttpResponse.json([
        { ...ticket, status: cancelado ? 'CANCELLED' : 'PAID' },
      ]),
    ),
    http.patch('/api/tickets/:code/cancel', () => {
      cancelado = true
      return HttpResponse.json({ ...ticket, status: 'CANCELLED' })
    }),
  )
  renderRuta('/mis-tickets', true)
  await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))
  await userEvent.click(
    screen.getByRole('button', { name: 'Confirmar cancelación' }),
  )
  expect(await screen.findByText('Cancelado')).toBeInTheDocument()
})
it('TEST-FE-TKT-008 409 cancelación mantiene modal', async () => {
  server.use(
    http.patch('/api/tickets/:code/cancel', () =>
      error(409, 'No se puede cancelar'),
    ),
  )
  renderRuta('/mis-tickets', true)
  await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))
  await userEvent.click(
    screen.getByRole('button', { name: 'Confirmar cancelación' }),
  )
  expect(await screen.findByText('No se puede cancelar')).toBeInTheDocument()
  expect(screen.getByRole('dialog')).toBeInTheDocument()
})
it.each([403, 404])(
  'TEST-FE-TKT-009 ticket ajeno %i no revela datos',
  async (status) => {
    server.use(http.get('/api/tickets/:code', () => error(status)))
    renderRuta('/mis-tickets/AJENO', true)
    expect(
      await screen.findByRole('heading', { name: 'Ticket no disponible' }),
    ).toBeInTheDocument()
    expect(screen.queryByText(ticket.eventName)).not.toBeInTheDocument()
  },
)
it('TEST-FE-TKT-010 no se puede cancelar USED', async () => {
  server.use(
    http.get('/api/tickets/by-user', () =>
      HttpResponse.json([{ ...ticket, status: 'USED' }]),
    ),
  )
  renderRuta('/mis-tickets', true)
  await screen.findByText('TKT-8F2A1C')
  expect(
    screen.queryByRole('button', { name: 'Cancelar' }),
  ).not.toBeInTheDocument()
})
it('TEST-FE-EVT-006 SOLD_OUT deshabilita compra', async () => {
  server.use(
    http.get('/api/events/:code', () =>
      HttpResponse.json({ ...evento, status: 'SOLD_OUT' }),
    ),
  )
  renderRuta('/eventos/CMF-2026', true)
  expect(
    await screen.findByRole('button', { name: 'Comprar entrada' }),
  ).toBeDisabled()
})
