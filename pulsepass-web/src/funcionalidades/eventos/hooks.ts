import { useQuery } from '@tanstack/react-query'
import { eventosApi } from './api'
export const useEventosPublicados = () =>
  useQuery({
    queryKey: ['eventos', 'publicados'],
    queryFn: ({ signal }) => eventosApi.publicados(signal),
    staleTime: 60000,
  })
export const useEvento = (code: string, enabled = true) =>
  useQuery({
    queryKey: ['evento', code],
    queryFn: ({ signal }) => eventosApi.evento(code, signal),
    enabled: enabled && !!code,
    staleTime: 30000,
  })
export const useEventosPorArtista = (stageName: string) =>
  useQuery({
    queryKey: ['eventos', 'artista', stageName],
    queryFn: ({ signal }) => eventosApi.porArtista(stageName, signal),
    enabled: !!stageName,
    staleTime: 60000,
  })
