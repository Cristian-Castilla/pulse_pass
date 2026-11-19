import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ticketsApi } from './api'
import type { Ticket } from './tipos'
export const useTicket = (code: string, enabled = true) =>
  useQuery({
    queryKey: ['ticket', code],
    queryFn: ({ signal }) => ticketsApi.ticket(code, signal),
    enabled: enabled && !!code,
    staleTime: 15000,
  })
export const useMisTickets = (email: string) =>
  useQuery({
    queryKey: ['tickets', 'usuario', email],
    queryFn: ({ signal }) => ticketsApi.porUsuario(email, signal),
    enabled: !!email,
    staleTime: 15000,
  })
export const useTicketsPagados = (code: string, enabled = true) =>
  useQuery({
    queryKey: ['tickets', 'pagados', code],
    queryFn: ({ signal }) => ticketsApi.pagados(code, signal),
    enabled: enabled && !!code,
    staleTime: 15000,
  })
export function useCancelarTicket() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ticketsApi.cancelar,
    onSuccess: async (t: Ticket) => {
      await Promise.all([
        client.invalidateQueries({
          queryKey: ['tickets', 'usuario', t.userEmail],
        }),
        client.invalidateQueries({ queryKey: ['ticket', t.ticketCode] }),
        client.invalidateQueries({
          queryKey: ['tickets', 'pagados', t.eventCode],
        }),
      ])
    },
  })
}
export function useUsarTicket() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ticketsApi.usar,
    onSuccess: async (t: Ticket) => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['ticket', t.ticketCode] }),
        client.invalidateQueries({
          queryKey: ['tickets', 'pagados', t.eventCode],
        }),
        client.invalidateQueries({
          queryKey: ['tickets', 'usuario', t.userEmail],
        }),
      ])
    },
  })
}
