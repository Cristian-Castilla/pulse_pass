import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { usuario, ticket } from './mocks/handlers'
import { renderRuta } from './utils'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

it('cuenta muestra el perfil propio del servidor sin exigirlo a auth/me', async () => {
  server.use(
    http.get('/api/auth/me', () =>
      HttpResponse.json({
        id: usuario.id,
        username: usuario.username,
        email: usuario.email,
        active: true,
        roles: ['ROLE_USER'],
      }),
    ),
    http.get('/api/users/by-email', ({ request }) => {
      expect(new URL(request.url).searchParams.get('email')).toBe(usuario.email)
      return HttpResponse.json({
        ...usuario,
        firstName: 'Andrea',
        lastName: 'Ruiz',
        birthDate: '1998-04-12',
      })
    }),
  )
  renderRuta('/cuenta', true)
  expect(await screen.findByText('Andrea Ruiz')).toBeInTheDocument()
  expect(screen.getByText('12/04/1998')).toHaveAttribute(
    'datetime',
    '1998-04-12',
  )
  expect(screen.getByText('Activa')).toBeInTheDocument()
  expect(
    screen.queryByLabelText('Vista bloqueada por contrato'),
  ).not.toBeInTheDocument()
})

it.each(['/mis-tickets/TKT-8F2A1C', '/staff/validacion'])(
  'muestra el titular del servidor en %s',
  async (ruta) => {
    server.use(
      http.get('/api/auth/me', () =>
        HttpResponse.json({ ...usuario, roles: ['ROLE_USER', 'ROLE_STAFF'] }),
      ),
    )
    renderRuta(ruta, true)
    if (ruta.startsWith('/staff')) {
      await userEvent.type(
        await screen.findByLabelText('Código de ticket'),
        ticket.ticketCode,
      )
      await userEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    }
    expect(await screen.findByText('Andrea Ruiz')).toBeInTheDocument()
    expect(
      screen.queryByLabelText('Vista bloqueada por contrato'),
    ).not.toBeInTheDocument()
  },
)

it('un perfil legado ausente se muestra como no informado, sin reutilizar el correo como nombre', async () => {
  server.use(
    http.get('/api/users/by-email', () =>
      HttpResponse.json({
        ...usuario,
        firstName: null,
        lastName: null,
        birthDate: null,
      }),
    ),
  )
  renderRuta('/cuenta', true)
  expect(await screen.findByText('Nombre no informado')).toBeInTheDocument()
  expect(screen.getByText('No informada')).toBeInTheDocument()
  expect(screen.queryByText('Andrea Ruiz')).not.toBeInTheDocument()
})
