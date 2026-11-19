import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './http'
export const crearQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: (intentos, error) =>
          intentos < 1 &&
          error.name !== 'AbortError' &&
          error instanceof ApiError &&
          (error.status === 0 || error.status >= 500),
        refetchOnWindowFocus: false,
      },
      mutations: { retry: false },
    },
  })
