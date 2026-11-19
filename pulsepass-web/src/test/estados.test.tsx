import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse, delay } from 'msw'
import { server } from './mocks/server'
import { renderRuta } from './utils'
import { usuario, error } from './mocks/handlers'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
const listas = [
  ['/eventos', '/api/events/published', 'Sin resultados para tu búsqueda'],
  ['/artistas', '/api/artists/active', 'Aún no hay artistas activos'],
  ['/sedes', '/api/venues/active', 'Aún no hay sedes activas'],
  ['/mis-tickets', '/api/tickets/by-user', 'Aún no tienes tickets'],
  [
    '/staff/eventos/CMF-2026/tickets',
    '/api/events/:code/tickets/paid',
    'No hay tickets pagados',
  ],
] as const
it.each(listas)(
  'estados cargando, vacío y error de %s',
  async (ruta, endpoint, mensaje) => {
    server.use(
      http.get('/api/auth/me', () =>
        HttpResponse.json({
          ...usuario,
          roles: ['ROLE_USER', 'ROLE_STAFF', 'ROLE_ADMIN'],
        }),
      ),
      http.get(endpoint, async () => {
        await delay(80)
        return HttpResponse.json([])
      }),
    )
    const r = renderRuta(ruta, true)
    expect(await screen.findByRole('status')).toHaveAttribute(
      'aria-busy',
      'true',
    )
    expect(await screen.findByText(mensaje)).toBeInTheDocument()
    server.use(http.get(endpoint, () => error(500, 'Detalle interno')))
    await r.client.invalidateQueries()
    expect(
      await screen.findByRole('heading', {
        name: 'Ocurrió un error inesperado. Inténtalo más tarde.',
      }),
    ).toBeInTheDocument()
  },
)
it('restauración descarta sessionStorage corrupto', async () => {
  sessionStorage.setItem('pulsepass.sesion', '{invalido')
  renderRuta('/cuenta')
  expect(
    await screen.findByRole('heading', { name: 'Inicia sesión' }),
  ).toBeInTheDocument()
  expect(sessionStorage.getItem('pulsepass.sesion')).toBeNull()
})
it('cuenta muestra el perfil del servidor por ambos endpoints de identidad propia', async () => {
  let porUsuario = 0
  server.use(
    http.get('/api/users/by-username', ({ request }) => {
      expect(new URL(request.url).searchParams.get('username')).toBe('andrea')
      porUsuario++
      return HttpResponse.json(usuario)
    }),
  )
  renderRuta('/cuenta', true)
  expect(await screen.findByText('Andrea Ruiz')).toBeInTheDocument()
  const { default: userEvent } = await import('@testing-library/user-event')
  await userEvent.click(
    screen.getByRole('button', { name: 'Consultar mi identidad por usuario' }),
  )
  await waitFor(() => expect(porUsuario).toBe(1))
})
