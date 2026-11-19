import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { server } from './mocks/server'
import { renderRuta } from './utils'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('TEST-FE-EVT-001 completa tarjetas con detalle y sede existentes, sin cambiar backend', async () => {
  renderRuta('/eventos')
  expect(
    await screen.findByRole('link', {
      name: /Caribbean Music Fest 2026.*Santa Marta/,
    }),
  ).toBeInTheDocument()
  expect(
    screen.queryByRole('heading', {
      name: 'Vista bloqueada por el contrato de la API',
    }),
  ).not.toBeInTheDocument()
})
it('TEST-FE-ART-002 artista muestra sus eventos y ciudad obtenida de la sede', async () => {
  renderRuta('/artistas/1')
  expect(
    await screen.findByRole('heading', { name: 'Solar Beat' }),
  ).toBeInTheDocument()
  expect(
    await screen.findByRole('link', {
      name: /Caribbean Music Fest 2026.*Santa Marta/,
    }),
  ).toBeInTheDocument()
})
