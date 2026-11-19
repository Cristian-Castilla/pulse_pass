import { beforeAll, afterAll, afterEach, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { server } from './mocks/server'
import { renderRuta } from './utils'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('buscar texto con espacios conserva la entrada y el parámetro URL', async () => {
  const r = renderRuta('/eventos')
  await userEvent.type(
    await screen.findByLabelText('Buscar por nombre'),
    'Caribbean Music',
  )
  expect(screen.getByLabelText('Buscar por nombre')).toHaveValue(
    'Caribbean Music',
  )
  expect(new URLSearchParams(r.router.state.location.search).get('q')).toBe(
    'Caribbean Music',
  )
})
it('copiar código informa error cuando el portapapeles no está disponible', async () => {
  renderRuta('/mis-tickets/TKT-8F2A1C', true)
  await screen.findByRole('button', { name: 'Copiar' })
  vi.stubGlobal('navigator', { clipboard: undefined })
  await userEvent.click(screen.getByRole('button', { name: 'Copiar' }))
  expect(
    await screen.findByText(
      'No pudimos copiarlo. Selecciona el código y cópialo manualmente.',
    ),
  ).toBeInTheDocument()
})
