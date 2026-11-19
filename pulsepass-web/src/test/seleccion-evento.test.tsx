import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { usuario } from './mocks/handlers'
import { renderRuta } from './utils'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('el código de evento literal buscar no se confunde con el selector de navegación', async () => {
  let consultas = 0
  server.use(
    http.get('/api/auth/me', () =>
      HttpResponse.json({ ...usuario, roles: ['ROLE_STAFF'] }),
    ),
    http.get('/api/events/buscar/tickets/paid', () => {
      consultas++
      return HttpResponse.json([])
    }),
  )
  renderRuta('/staff/eventos/buscar/tickets', true)
  expect(await screen.findByText('No hay tickets pagados')).toBeInTheDocument()
  expect(consultas).toBe(1)
})
