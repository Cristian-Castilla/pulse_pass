import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { usuario } from './mocks/handlers'
import { renderRuta } from './utils'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('ADMIN recupera código de la sesión y consulta nombre/estado sin duplicar datos', async () => {
  sessionStorage.setItem('pulsepass.creados', JSON.stringify(['CMF-2026']))
  server.use(
    http.get('/api/auth/me', () =>
      HttpResponse.json({ ...usuario, roles: ['ROLE_ADMIN'] }),
    ),
  )
  renderRuta('/admin/eventos', true)
  expect(
    await screen.findByText('Caribbean Music Fest 2026'),
  ).toBeInTheDocument()
  expect(screen.getByText('Publicado')).toBeInTheDocument()
  expect(
    screen.getByRole('link', { name: 'Gestionar CMF-2026' }),
  ).toHaveAttribute('href', '/admin/eventos/CMF-2026')
})
