import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { usuario, artista, error } from './mocks/handlers'
import { renderRuta } from './utils'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('TEST-FE-ADM-006 un 409 al asociar se muestra sin modificar la lista', async () => {
  server.use(
    http.get('/api/auth/me', () =>
      HttpResponse.json({ ...usuario, roles: ['ROLE_ADMIN'] }),
    ),
    http.get('/api/artists/active', () =>
      HttpResponse.json([
        artista,
        { ...artista, id: 2, stageName: 'Neon Waves' },
      ]),
    ),
    http.post('/api/events/:code/artists/:id', () =>
      error(409, 'Artista duplicado'),
    ),
  )
  renderRuta('/admin/eventos/CMF-2026', true)
  await userEvent.selectOptions(
    await screen.findByLabelText('Agregar artista'),
    '2',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Asociar' }))
  expect(await screen.findByText('Artista duplicado')).toBeInTheDocument()
  expect(
    screen.queryByRole('listitem', { name: 'Neon Waves' }),
  ).not.toBeInTheDocument()
})
it('STAFF puro puede entrar a validación pero no a administración', async () => {
  server.use(
    http.get('/api/auth/me', () =>
      HttpResponse.json({ ...usuario, roles: ['ROLE_STAFF'] }),
    ),
  )
  const r = renderRuta('/staff/validacion', true)
  expect(
    await screen.findByRole('heading', { name: 'Validación de ingreso' }),
  ).toBeInTheDocument()
  await r.router.navigate('/admin/eventos')
  expect(
    await screen.findByRole('heading', { name: '403' }),
  ).toBeInTheDocument()
})
