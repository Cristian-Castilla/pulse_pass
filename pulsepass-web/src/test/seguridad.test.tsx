import { beforeAll, afterAll, afterEach, it, expect, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http as mswHttp, HttpResponse, delay } from 'msw'
import { z } from 'zod'
import { server } from './mocks/server'
import { usuario, ticket, error } from './mocks/handlers'
import { renderRuta } from './utils'
import { configurarSesion, http, ApiError } from '../lib/http'
import { destinoSeguro, CodigoSchema } from '../lib/parametros'
import { CrearEventoSchema } from '../funcionalidades/eventos/tipos'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('TEST-FE-GEN-002 errorElement nunca muestra stack ni mensajes internos', async () => {
  server.use(
    mswHttp.get('/api/events/published', () =>
      error(500, 'stack interno privado'),
    ),
  )
  renderRuta('/')
  expect(
    await screen.findByRole('heading', {
      name: 'Ocurrió un error inesperado. Inténtalo más tarde.',
    }),
  ).toBeInTheDocument()
  expect(screen.queryByText('stack interno privado')).not.toBeInTheDocument()
})
it.each([
  ['ROLE_USER', 'Mis tickets'],
  ['ROLE_STAFF', 'Validar ingreso'],
  ['ROLE_ADMIN', 'Administrar eventos'],
])('TEST-FE-GEN-003 navbar de %s', async (rol, enlace) => {
  server.use(
    mswHttp.get('/api/auth/me', () =>
      HttpResponse.json({ ...usuario, roles: [rol] }),
    ),
  )
  renderRuta('/', true)
  expect(await screen.findByRole('link', { name: enlace })).toBeInTheDocument()
  if (rol !== 'ROLE_USER')
    expect(
      screen.queryByRole('link', { name: 'Mis tickets' }),
    ).not.toBeInTheDocument()
})
it('TEST-FE-GEN-004 menú móvil abre y cierra por teclado', async () => {
  renderRuta('/')
  const boton = await screen.findByRole('button', { name: 'Abrir menú' })
  boton.focus()
  await userEvent.keyboard('{Enter}')
  expect(screen.getByRole('button', { name: 'Cerrar menú' })).toHaveAttribute(
    'aria-expanded',
    'true',
  )
  screen.getByRole('navigation', { name: 'Principal' }).focus()
  await userEvent.click(screen.getByRole('button', { name: 'Cerrar menú' }))
  expect(screen.getByRole('button', { name: 'Abrir menú' })).toHaveAttribute(
    'aria-expanded',
    'false',
  )
})
it('401 protegido elimina identidad y caché y conserva destino', async () => {
  server.use(mswHttp.get('/api/tickets/by-user', () => error(401)))
  const { client, router } = renderRuta('/mis-tickets', true)
  client.setQueryData(['privado'], ticket)
  expect(await screen.findByText('Tu sesión expiró')).toBeInTheDocument()
  expect(sessionStorage.getItem('pulsepass.sesion')).toBeNull()
  expect(client.getQueryData(['privado'])).toBeUndefined()
  expect(router.state.location.state).toMatchObject({
    desde: '/mis-tickets',
    motivo: 'expirada',
  })
})
it('respuesta tardía de otra sesión se descarta', async () => {
  let token: string | null = 'sesion-a'
  configurarSesion(
    () => token,
    () => {},
  )
  server.use(
    mswHttp.get('/api/lento', async () => {
      await delay(30)
      return HttpResponse.json({ privado: true })
    }),
  )
  const peticion = http('/lento', z.object({ privado: z.boolean() }))
  token = null
  await expect(peticion).rejects.toMatchObject({ name: 'AbortError' })
  configurarSesion(
    () => null,
    () => {},
  )
})
it('un 401 tardío no expulsa la identidad nueva', async () => {
  let token = 'sesion-a'
  const cerrar = vi.fn()
  configurarSesion(() => token, cerrar)
  server.use(
    mswHttp.get('/api/lento', async () => {
      await delay(30)
      return error(401)
    }),
  )
  const peticion = http('/lento', z.unknown())
  token = 'sesion-b'
  await expect(peticion).rejects.toMatchObject({ name: 'AbortError' })
  expect(cerrar).not.toHaveBeenCalled()
  configurarSesion(
    () => null,
    () => {},
  )
})
it('redirecciones externas y códigos maliciosos no se aceptan', () => {
  for (const url of [
    'https://externo.test',
    '//externo.test',
    '/\\externo.test',
    '/login',
    '/registro',
  ])
    expect(destinoSeguro(url)).toBe('/')
  expect(destinoSeguro('/eventos/CMF-2026?q=musica')).toBe(
    '/eventos/CMF-2026?q=musica',
  )
  expect(CodigoSchema.safeParse('../privado').success).toBe(false)
})
it('fecha imposible no se envía al crear evento', () => {
  expect(
    CrearEventoSchema.safeParse({
      eventCode: 'A',
      name: 'A',
      venueCode: 'V',
      category: 'MUSIC',
      eventDate: '2026-02-30T20:00',
      minimumAge: 0,
    }).success,
  ).toBe(false)
})
it('red caída se convierte en ApiError seguro', async () => {
  server.use(mswHttp.get('/api/red', () => HttpResponse.error()))
  await expect(http('/red', z.unknown())).rejects.toBeInstanceOf(ApiError)
})
it('roles combinados mantienen acceso y compra oculta a STAFF/ADMIN', async () => {
  server.use(
    mswHttp.get('/api/auth/me', () =>
      HttpResponse.json({
        ...usuario,
        roles: ['ROLE_USER', 'ROLE_STAFF', 'ROLE_ADMIN'],
      }),
    ),
  )
  renderRuta('/eventos/CMF-2026', true)
  await screen.findByRole('link', { name: 'Administrar eventos' })
  await screen.findByRole('heading', { name: 'Caribbean Music Fest 2026' })
  expect(
    screen.queryByRole('button', { name: 'Comprar entrada' }),
  ).not.toBeInTheDocument()
})
it('cerrar sesión aborta queries sin recuperar datos anteriores', async () => {
  const { client } = renderRuta('/mis-tickets', true)
  await screen.findByRole('heading', { name: 'Mis tickets' })
  await userEvent.click(screen.getByText('andrea'))
  await userEvent.click(screen.getByRole('button', { name: 'Salir' }))
  await waitFor(() =>
    expect(
      client.getQueryData(['tickets', 'usuario', usuario.email]),
    ).toBeUndefined(),
  )
})
