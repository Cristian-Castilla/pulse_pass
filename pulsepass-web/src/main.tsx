import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { crearRutas } from './router'
import { crearQueryClient } from './lib/queryClient'
import './index.css'
const root = document.getElementById('root')
if (!root) throw new Error('Falta el contenedor principal')
createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={crearQueryClient()}>
      <RouterProvider router={createBrowserRouter(crearRutas())} />
    </QueryClientProvider>
  </StrictMode>,
)
