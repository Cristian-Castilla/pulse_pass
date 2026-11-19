import { beforeAll, afterAll, afterEach, beforeEach, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { usuario, evento, ticket, artista, error } from './mocks/handlers'
import { renderRuta } from './utils'
import { precioCOP } from '../lib/formato'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() =>
  server.use(
    http.get('/api/auth/me', () =>
      HttpResponse.json({ ...usuario, roles: ['ROLE_ADMIN'] }),
    ),
  ),
)
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
async function buscar() {
  await userEvent.type(
    await screen.findByLabelText('Código de ticket'),
    'TKT-8F2A1C',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Buscar' }))
}
it('TEST-FE-STF-001 buscar y registrar ingreso', async () => {
  server.use(
    http.get('/api/tickets/:code', () =>
      HttpResponse.json({ ...ticket, status: 'USED' }),
    ),
  )
  renderRuta('/staff/validacion', true)
  await buscar()
  await userEvent.click(
    await screen.findByRole('button', { name: 'Marcar como usado' }),
  )
  expect(await screen.findByText('Ingreso registrado.')).toBeInTheDocument()
  expect(await screen.findByText('Usado')).toBeInTheDocument()
})
it('TEST-FE-STF-002 segundo uso muestra 409', async () => {
  server.use(
    http.patch('/api/tickets/:code/use', () =>
      error(409, 'El ticket ya fue utilizado'),
    ),
  )
  renderRuta('/staff/validacion', true)
  await buscar()
  await userEvent.click(
    await screen.findByRole('button', { name: 'Marcar como usado' }),
  )
  expect(
    await screen.findByText('El ticket ya fue utilizado'),
  ).toBeInTheDocument()
})
it('TEST-FE-STF-003 ticket inexistente muestra 404', async () => {
  server.use(http.get('/api/tickets/:code', () => error(404)))
  renderRuta('/staff/validacion', true)
  await buscar()
  expect(await screen.findByText('Ticket no encontrado')).toBeInTheDocument()
})
it('TEST-FE-STF-004 tabla de tickets pagados muestra titular, importe y acción', async () => {
  let usado = false
  server.use(
    http.get('/api/events/:code/tickets/paid', () =>
      HttpResponse.json(usado ? [] : [ticket]),
    ),
    http.patch('/api/tickets/:code/use', ({ params }) => {
      expect(params.code).toBe(ticket.ticketCode)
      usado = true
      return HttpResponse.json({ ...ticket, status: 'USED' })
    }),
  )
  renderRuta('/staff/eventos/CMF-2026/tickets', true)
  expect(await screen.findByRole('table')).toBeInTheDocument()
  expect(
    screen.getByRole('columnheader', { name: 'Titular' }),
  ).toBeInTheDocument()
  expect(screen.getByText('Andrea Ruiz')).toBeInTheDocument()
  expect(screen.getByText('TKT-8F2A1C')).toBeInTheDocument()
  expect(
    screen.getByText(precioCOP(ticket.price), { normalizer: (text) => text }),
  ).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Usar' })).toBeInTheDocument()
  expect(
    screen.queryByLabelText('Vista bloqueada por contrato'),
  ).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Usar' }))
  expect(await screen.findByText('No hay tickets pagados')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Usar' })).not.toBeInTheDocument()
})
it.each([403, 409])(
  'usar desde la tabla conserva la fila y muestra el error %i',
  async (status) => {
    server.use(
      http.patch('/api/tickets/:code/use', () =>
        error(status, 'Ingreso rechazado'),
      ),
    )
    renderRuta('/staff/eventos/CMF-2026/tickets', true)
    await userEvent.click(await screen.findByRole('button', { name: 'Usar' }))
    expect(
      await screen.findByText(
        status === 403
          ? 'No tiene permisos para realizar esta operación'
          : 'Ingreso rechazado',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('Andrea Ruiz')).toBeInTheDocument()
  },
)
it('TEST-FE-ADM-001 formulario vacío no invoca creación', async () => {
  let llamadas = 0
  server.use(
    http.post('/api/events', () => {
      llamadas++
      return error(500)
    }),
  )
  renderRuta('/admin/eventos/nuevo', true)
  await userEvent.click(
    await screen.findByRole('button', { name: 'Crear evento' }),
  )
  expect(await screen.findByText('Ingresa el código')).toBeInTheDocument()
  expect(llamadas).toBe(0)
})
async function crear() {
  await userEvent.type(
    await screen.findByLabelText('Código del evento'),
    'NUEVO-2026',
  )
  await userEvent.type(screen.getByLabelText('Nombre'), 'Nuevo festival')
  await userEvent.type(
    screen.getByLabelText('Fecha y hora'),
    '2026-12-12T20:00',
  )
  await userEvent.selectOptions(screen.getByLabelText('Sede'), 'VEN-SMR-01')
  await userEvent.click(screen.getByRole('button', { name: 'Crear evento' }))
}
it('TEST-FE-ADM-002 crear 201 navega a gestión', async () => {
  const { router } = renderRuta('/admin/eventos/nuevo', true)
  await crear()
  await waitFor(() =>
    expect(router.state.location.pathname).toBe('/admin/eventos/NUEVO-2026'),
  )
  expect(
    JSON.parse(sessionStorage.getItem('pulsepass.creados') ?? '[]'),
  ).toContain('NUEVO-2026')
})
it('TEST-FE-ADM-003 details de 400 se asignan al campo', async () => {
  server.use(
    http.post('/api/events', () =>
      error(400, 'Inválido', { name: 'Nombre rechazado' }),
    ),
  )
  renderRuta('/admin/eventos/nuevo', true)
  await crear()
  expect(await screen.findByText('Nombre rechazado')).toBeInTheDocument()
  expect(screen.getByLabelText('Nombre')).toHaveAttribute(
    'aria-invalid',
    'true',
  )
})
it('TEST-FE-ADM-004 publicar actualiza badge', async () => {
  let publicado = false
  server.use(
    http.get('/api/events/:code', () =>
      HttpResponse.json({
        ...evento,
        status: publicado ? 'PUBLISHED' : 'DRAFT',
      }),
    ),
    http.patch('/api/events/:code/publish', () => {
      publicado = true
      return HttpResponse.json(evento)
    }),
  )
  renderRuta('/admin/eventos/CMF-2026', true)
  await userEvent.click(
    await screen.findByRole('button', { name: 'Publicar evento' }),
  )
  await userEvent.click(
    screen.getByRole('button', { name: 'Confirmar publicación' }),
  )
  expect(await screen.findByText('Publicado')).toBeInTheDocument()
})
it('TEST-FE-ADM-005 conflicto al publicar mantiene aviso', async () => {
  server.use(
    http.get('/api/events/:code', () =>
      HttpResponse.json({ ...evento, status: 'DRAFT' }),
    ),
    http.patch('/api/events/:code/publish', () =>
      error(409, 'Transición inválida'),
    ),
  )
  renderRuta('/admin/eventos/CMF-2026', true)
  await userEvent.click(
    await screen.findByRole('button', { name: 'Publicar evento' }),
  )
  await userEvent.click(
    screen.getByRole('button', { name: 'Confirmar publicación' }),
  )
  expect(await screen.findByText('Transición inválida')).toBeInTheDocument()
})
it('TEST-FE-ADM-006 asociar muestra artista y 409 duplicado', async () => {
  let asociado = false
  const segundo = { ...artista, id: 2, stageName: 'Neon Waves' }
  server.use(
    http.get('/api/artists/active', () =>
      HttpResponse.json([artista, segundo]),
    ),
    http.get('/api/events/:code', () =>
      HttpResponse.json({
        ...evento,
        artists: asociado ? [artista, segundo] : [artista],
      }),
    ),
    http.post('/api/events/:code/artists/:id', () => {
      if (asociado) return error(409, 'Artista duplicado')
      asociado = true
      return HttpResponse.json({ ...evento, artists: [artista, segundo] })
    }),
  )
  renderRuta('/admin/eventos/CMF-2026', true)
  await userEvent.selectOptions(
    await screen.findByLabelText('Agregar artista'),
    '2',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Asociar' }))
  expect(await screen.findByText('Artista asociado.')).toBeInTheDocument()
  expect(
    await screen.findByRole('listitem', { name: 'Neon Waves' }),
  ).toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'Neon Waves' })).toBeDisabled()
})
