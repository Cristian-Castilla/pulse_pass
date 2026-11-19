import { z } from 'zod'
import { FechaHoraSchema } from '../eventos/tipos'
export const TipoTicketSchema = z.enum([
  'GENERAL',
  'VIP',
  'BACKSTAGE',
  'STUDENT',
])
export const EstadoTicketSchema = z.enum([
  'RESERVED',
  'PAID',
  'CANCELLED',
  'USED',
])
export const TicketSchema = z.object({
  id: z.number().int().positive(),
  ticketCode: z.string(),
  type: TipoTicketSchema,
  price: z.number().nonnegative(),
  status: EstadoTicketSchema,
  purchaseDate: FechaHoraSchema,
  userEmail: z.email(),
  userFullName: z.string().min(1).nullable(),
  eventCode: z.string(),
  eventName: z.string(),
})
export type Ticket = z.infer<typeof TicketSchema>
export const CompraSchema = z.object({ type: TipoTicketSchema })
export type Compra = z.infer<typeof CompraSchema>
