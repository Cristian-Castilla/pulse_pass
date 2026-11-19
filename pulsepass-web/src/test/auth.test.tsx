import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { usuario, error } from './mocks/handlers'
import { renderRuta } from './utils'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
async function login() {
  await userEvent.type(
    await screen.findByLabelText('Usuario o correo'),
    'andrea',
  )
  await userEvent.type(
    screen.getByLabelText('Contraseña'),
    'UnaFraseLarga2026!',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}
it('TEST-FE-AUTH-001 login válido conserva el destino', async () => {
  const { router } = renderRuta('/mis-tickets')
  await login()
  await waitFor(() =>
    expect(router.state.location.pathname).toBe('/mis-tickets'),
  )
  expect(
    await screen.findByRole('heading', { name: 'Mis tickets' }),
  ).toBeInTheDocument()
})
it('TEST-FE-AUTH-002 login 401 genérico', async () => {
  server.use(
    http.post('/api/auth/login', () => error(401, 'Usuario no existe')),
  )
  renderRuta('/login')
  await login()
  expect(await screen.findByText('Credenciales inválidas')).toBeInTheDocument()
  expect(screen.queryByText('Usuario no existe')).not.toBeInTheDocument()
})
it('TEST-FE-AUTH-003 login inválido no envía HTTP', async () => {
  let llamadas = 0
  server.use(
    http.post('/api/auth/login', () => {
      llamadas++
      return error(500)
    }),
  )
  renderRuta('/login')
  await userEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))
  expect(
    await screen.findByText('Ingresa tu usuario o correo'),
  ).toBeInTheDocument()
  expect(llamadas).toBe(0)
})
it('TEST-FE-AUTH-004 registro corto no envía HTTP', async () => {
  let llamadas = 0
  server.use(
    http.post('/api/users', () => {
      llamadas++
      return error(500)
    }),
  )
  renderRuta('/registro')
  await userEvent.type(
    await screen.findByLabelText('Contraseña (mínimo 12 caracteres)'),
    '12345678901',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))
  expect(await screen.findByText('Mínimo 12 caracteres')).toBeInTheDocument()
  expect(llamadas).toBe(0)
})
async function registro() {
  await userEvent.type(
    await screen.findByLabelText('Nombre de usuario'),
    'andrea',
  )
  await userEvent.type(
    screen.getByLabelText('Correo electrónico'),
    'andrea@pulsepass.test',
  )
  await userEvent.type(screen.getByLabelText('Nombres'), 'Andrea')
  await userEvent.type(screen.getByLabelText('Apellidos'), 'Ruiz')
  await userEvent.type(
    screen.getByLabelText('Fecha de nacimiento'),
    '1998-04-12',
  )
  await userEvent.type(
    screen.getByLabelText('Contraseña (mínimo 12 caracteres)'),
    'UnaFraseLarga2026!',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))
}
it('TEST-FE-AUTH-005 registro 201 vuelve a login', async () => {
  renderRuta('/registro')
  await registro()
  expect(
    await screen.findByText('Cuenta creada, inicia sesión'),
  ).toBeInTheDocument()
})
it('TEST-FE-AUTH-006 registro duplicado no revela detalles', async () => {
  server.use(http.post('/api/users', () => error(409, 'Detalle privado')))
  renderRuta('/registro')
  await registro()
  expect(
    await screen.findByText('El usuario o correo ya está registrado'),
  ).toBeInTheDocument()
  expect(screen.queryByText('Detalle privado')).not.toBeInTheDocument()
})
it('TEST-FE-AUTH-007 guarda anónimo mantiene desde', async () => {
  const { router } = renderRuta('/mis-tickets?estado=PAID')
  await screen.findByRole('heading', { name: 'Inicia sesión' })
  expect(router.state.location.state).toMatchObject({
    desde: '/mis-tickets?estado=PAID',
  })
})
it('TEST-FE-AUTH-008 USER en admin recibe 403', async () => {
  renderRuta('/admin/eventos', true)
  expect(
    await screen.findByRole('heading', { name: '403' }),
  ).toBeInTheDocument()
})
it('TEST-FE-AUTH-009 ADMIN entra a admin', async () => {
  server.use(
    http.get('/api/auth/me', () =>
      HttpResponse.json({ ...usuario, roles: ['ROLE_ADMIN'] }),
    ),
  )
  renderRuta('/admin/eventos', true)
  expect(
    await screen.findByRole('heading', { name: 'Administración de eventos' }),
  ).toBeInTheDocument()
})
it('TEST-FE-AUTH-010 salir limpia token y caché', async () => {
  const { client } = renderRuta('/mis-tickets', true)
  await screen.findByRole('heading', { name: 'Mis tickets' })
  client.setQueryData(['privado'], { secreto: 'ficticio' })
  await userEvent.click(screen.getByText('andrea'))
  await userEvent.click(screen.getByRole('button', { name: 'Salir' }))
  await waitFor(() =>
    expect(sessionStorage.getItem('pulsepass.sesion')).toBeNull(),
  )
  expect(client.getQueryData(['privado'])).toBeUndefined()
})
it('TEST-FE-AUTH-011 token expirado no restaura identidad', async () => {
  sessionStorage.setItem(
    'pulsepass.sesion',
    JSON.stringify({ token: 'caducado', expiraEn: Date.now() - 1 }),
  )
  renderRuta('/cuenta')
  expect(
    await screen.findByRole('heading', { name: 'Inicia sesión' }),
  ).toBeInTheDocument()
  expect(sessionStorage.getItem('pulsepass.sesion')).toBeNull()
})
