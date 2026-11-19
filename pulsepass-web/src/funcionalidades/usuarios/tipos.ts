import { z } from 'zod'
export const UsuarioSchema = z.object({
  id: z.number().int().positive(),
  username: z.string(),
  email: z.email(),
  active: z.boolean(),
  firstName: z.string().min(1).nullable(),
  lastName: z.string().min(1).nullable(),
  birthDate: z.iso.date().nullable(),
})
export type Usuario = z.infer<typeof UsuarioSchema>
