import { render } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { crearRutas } from '../router'
import { crearQueryClient } from '../lib/queryClient'
export function renderRuta(ruta = '/', autenticado = false) {
  if (autenticado)
    sessionStorage.setItem(
      'pulsepass.sesion',
      JSON.stringify({
        token: 'token-ficticio-de-pruebas',
        expiraEn: Date.now() + 900000,
      }),
    )
  const client = crearQueryClient()
  client.setDefaultOptions({
    queries: { retry: false },
    mutations: { retry: false },
  })
  const router = createMemoryRouter(crearRutas(), { initialEntries: [ruta] })
  const view = render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return { ...view, client, router }
}
