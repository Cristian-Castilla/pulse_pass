import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { usuario, error } from './mocks/handlers'
import { renderRuta } from './utils'
import ErrorPagina from '../pages/ErrorPagina'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('un 403 del servidor prevalece sobre el ADMIN local al cargar una página', async () => {
  server.use(
    http.get('/api/auth/me', () =>
      HttpResponse.json({ ...usuario, roles: ['ROLE_ADMIN'] }),
    ),
    http.get('/api/events/:code', () => error(403)),
  )
  const { router } = renderRuta('/admin/eventos/CMF-2026', true)
  expect(
    await screen.findByRole('heading', { name: '403' }),
  ).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/403')
})
it('errorElement usa error de ruta y no muestra el mensaje interno', async () => {
  const router = createMemoryRouter(
    [
      {
        path: '/',
        loader: () => {
          throw new Error('stack y mensaje privado')
        },
        errorElement: <ErrorPagina />,
      },
    ],
    { initialEntries: ['/'] },
  )
  render(<RouterProvider router={router} />)
  expect(
    await screen.findByRole('heading', { name: '¡Ups! Algo salió mal.' }),
  ).toBeInTheDocument()
  expect(screen.queryByText('stack y mensaje privado')).not.toBeInTheDocument()
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: 'Reintentar' }),
    ).toBeInTheDocument(),
  )
})
