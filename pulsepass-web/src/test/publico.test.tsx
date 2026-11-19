import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { server } from './mocks/server'
import { sede, error } from './mocks/handlers'
import { renderRuta } from './utils'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('TEST-FE-GEN-001 ruta inexistente muestra 404', async () => {
  renderRuta('/desconocida')
  expect(
    await screen.findByRole('heading', { name: '404' }),
  ).toBeInTheDocument()
})
it('TEST-FE-VEN-001 lista, detalle y 404 de sede', async () => {
  const r = renderRuta('/sedes')
  await userEvent.click(
    await screen.findByRole('link', { name: new RegExp(sede.name) }),
  )
  expect(await screen.findByText(sede.address)).toBeInTheDocument()
  server.use(http.get('/api/venues/:code', () => error(404)))
  await r.router.navigate('/sedes/NO-EXISTE')
  expect(
    await screen.findByRole('heading', { name: 'No encontramos esta página' }),
  ).toBeInTheDocument()
})
it('TEST-FE-ART-001 lista artistas activos', async () => {
  renderRuta('/artistas')
  expect(
    await screen.findByRole('link', { name: /Solar Beat/ }),
  ).toBeInTheDocument()
})
it('TEST-FE-ART-003 id inválido no llama a la API', async () => {
  let llamadas = 0
  server.use(
    http.get('/api/artists/:id', () => {
      llamadas++
      return error(500)
    }),
  )
  renderRuta('/artistas/no-numerico')
  expect(
    await screen.findByRole('heading', { name: '404' }),
  ).toBeInTheDocument()
  expect(llamadas).toBe(0)
})
it('TEST-FE-EVT-002 cartelera vacía', async () => {
  server.use(http.get('/api/events/published', () => HttpResponse.json([])))
  renderRuta('/eventos')
  expect(
    await screen.findByText('Sin resultados para tu búsqueda'),
  ).toBeInTheDocument()
})
it('TEST-FE-EVT-003 filtros actualizan URL', async () => {
  const { router } = renderRuta('/eventos')
  await userEvent.type(
    await screen.findByLabelText('Buscar por nombre'),
    'jazz',
  )
  expect(router.state.location.search).toContain('q=jazz')
  await userEvent.selectOptions(screen.getByLabelText('Categoría'), 'MUSIC')
  expect(router.state.location.search).toContain('categoria=MUSIC')
})
it('TEST-FE-EVT-004 detalle muestra evento, artistas y sede', async () => {
  renderRuta('/eventos/CMF-2026')
  expect(
    await screen.findByRole('heading', { name: 'Caribbean Music Fest 2026' }),
  ).toBeInTheDocument()
  expect(await screen.findByText('Santa Marta')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Solar Beat' })).toBeInTheDocument()
})
it('TEST-FE-EVT-005 evento inexistente no revela borrador', async () => {
  server.use(http.get('/api/events/:code', () => error(404)))
  renderRuta('/eventos/SECRETO')
  expect(
    await screen.findByRole('heading', { name: 'Evento no disponible' }),
  ).toBeInTheDocument()
})
it('catálogo toma la ciudad real del endpoint de sedes', async () => {
  server.use(
    http.get('/api/venues/:code', () =>
      HttpResponse.json({ ...sede, city: 'Ciudad real de la API' }),
    ),
  )
  renderRuta('/eventos')
  expect(
    await screen.findByRole('link', {
      name: /Caribbean Music Fest 2026.*Ciudad real de la API/,
    }),
  ).toBeInTheDocument()
  expect(screen.queryByText('Santa Marta')).not.toBeInTheDocument()
})
