import { z } from 'zod'
export const ArtistaSchema = z.object({
  id: z.number().int().positive(),
  stageName: z.string(),
  country: z.string().nullable(),
  genre: z.string().nullable(),
  active: z.boolean(),
})
export type Artista = z.infer<typeof ArtistaSchema>
