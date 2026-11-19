import { z } from 'zod'
import { ArtistaSchema } from '../artistas/tipos'
export const CategoriaSchema = z.enum([
  'MUSIC',
  'SPORTS',
  'TECHNOLOGY',
  'EDUCATION',
  'CULTURE',
  'ENTERTAINMENT',
])
export const EstadoEventoSchema = z.enum([
  'DRAFT',
  'PUBLISHED',
  'SOLD_OUT',
  'CANCELLED',
  'FINISHED',
])
export const FechaHoraSchema = z.iso.datetime({ local: true })
export const EventoResumenSchema = z.object({
  id: z.number().int().positive(),
  eventCode: z.string().min(1),
  name: z.string(),
  category: CategoriaSchema,
  status: EstadoEventoSchema,
  eventDate: FechaHoraSchema,
})
export const EventoSchema = EventoResumenSchema.extend({
  description: z.string().nullable(),
  minimumAge: z.number().int().nonnegative(),
  venueCode: z.string(),
  venueName: z.string(),
  artists: z.array(ArtistaSchema),
})
export const CrearEventoSchema = z.object({
  eventCode: z.string().trim().min(1, 'Ingresa el código'),
  name: z.string().trim().min(1, 'Ingresa el nombre'),
  description: z.string().max(500, 'Máximo 500 caracteres').optional(),
  category: CategoriaSchema,
  eventDate: z
    .string()
    .regex(
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/,
      'Ingresa fecha y hora válidas',
    )
    .refine(
      (v) => FechaHoraSchema.safeParse(v.length === 16 ? `${v}:00` : v).success,
      'Fecha inválida',
    ),
  minimumAge: z
    .number()
    .int('Ingresa un entero')
    .nonnegative('La edad no puede ser negativa'),
  venueCode: z.string().min(1, 'Selecciona una sede'),
})
export type Evento = z.infer<typeof EventoSchema>
export type EventoResumen = z.infer<typeof EventoResumenSchema>
export type Categoria = z.infer<typeof CategoriaSchema>
export type CrearEvento = z.infer<typeof CrearEventoSchema>
