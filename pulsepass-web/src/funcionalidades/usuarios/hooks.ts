import { useQuery } from '@tanstack/react-query'
import { usuariosApi } from './api'
export const useUsuario = (valor: string, por: 'email' | 'username') =>
  useQuery({
    queryKey: ['usuario', por, valor],
    queryFn: ({ signal }) =>
      por === 'email'
        ? usuariosApi.porEmail(valor, signal)
        : usuariosApi.porUsername(valor, signal),
    enabled: !!valor,
    staleTime: 60000,
  })
