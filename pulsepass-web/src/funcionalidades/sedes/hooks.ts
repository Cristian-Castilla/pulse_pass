import { useQuery } from '@tanstack/react-query'
import { sedesApi } from './api'
export const useSedesActivas = () =>
  useQuery({
    queryKey: ['sedes', 'activas'],
    queryFn: ({ signal }) => sedesApi.activas(signal),
    staleTime: 300000,
  })
export const useSede = (code: string, enabled = true) =>
  useQuery({
    queryKey: ['sede', code],
    queryFn: ({ signal }) => sedesApi.sede(code, signal),
    enabled: enabled && !!code,
    staleTime: 300000,
  })
