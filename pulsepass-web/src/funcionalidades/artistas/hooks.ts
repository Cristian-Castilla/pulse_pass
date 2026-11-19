import { useQuery } from '@tanstack/react-query'
import { artistasApi } from './api'
export const useArtistasActivos = () =>
  useQuery({
    queryKey: ['artistas', 'activos'],
    queryFn: ({ signal }) => artistasApi.activos(signal),
    staleTime: 300000,
  })
export const useArtista = (id: number, enabled = true) =>
  useQuery({
    queryKey: ['artista', id],
    queryFn: ({ signal }) => artistasApi.artista(id, signal),
    enabled,
    staleTime: 300000,
  })
export const useArtistaPorNombre = (nombre: string) =>
  useQuery({
    queryKey: ['artista', 'nombre', nombre],
    queryFn: ({ signal }) => artistasApi.porNombre(nombre, signal),
    enabled: !!nombre,
    staleTime: 300000,
  })
