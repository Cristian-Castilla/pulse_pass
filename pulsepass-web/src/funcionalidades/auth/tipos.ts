import { z } from 'zod'
import { UsuarioSchema } from '../usuarios/tipos'
export const RolSchema = z.enum(['USER', 'STAFF', 'ADMIN'])
export type Rol = z.infer<typeof RolSchema>
export const UsuarioAutenticadoSchema = UsuarioSchema.omit({
  firstName: true,
  lastName: true,
  birthDate: true,
}).extend({
  roles: z.array(
    z
      .enum(['ROLE_USER', 'ROLE_STAFF', 'ROLE_ADMIN'])
      .transform((r) => RolSchema.parse(r.slice(5))),
  ),
})
export type UsuarioAutenticado = z.infer<typeof UsuarioAutenticadoSchema>
export const AuthSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal('Bearer'),
  expiresIn: z.number().int().positive().max(86400),
})
export const SesionGuardadaSchema = z.object({
  token: z.string().min(1),
  expiraEn: z.number().finite().positive(),
})
export const LoginSchema = z.object({
  identifier: z.string().trim().min(1, 'Ingresa tu usuario o correo'),
  password: z.string().min(1, 'Ingresa tu contraseña'),
})
const FechaNacimientoSchema = z.iso
  .date('Ingresa una fecha válida')
  .refine(
    (v) => v <= new Date().toLocaleDateString('sv-SE'),
    'La fecha no puede ser futura',
  )
export const RegistroSchema = z.object({
  username: z.string().trim().min(1, 'Ingresa un usuario'),
  email: z.email('Ingresa un correo válido'),
  firstName: z.string().trim().min(1, 'Ingresa tus nombres'),
  lastName: z.string().trim().min(1, 'Ingresa tus apellidos'),
  birthDate: FechaNacimientoSchema,
  password: z
    .string()
    .min(12, 'Mínimo 12 caracteres')
    .refine(
      (p) => new TextEncoder().encode(p).length <= 72,
      'Máximo 72 bytes UTF-8',
    ),
})
export type Login = z.infer<typeof LoginSchema>
export type Registro = z.infer<typeof RegistroSchema>
export type Sesion =
  | { estado: 'cargando' }
  | { estado: 'anonimo'; motivo?: 'expirada' | 'salida'; desde?: string }
  | {
      estado: 'autenticado'
      token: string
      expiraEn: number
      usuario: UsuarioAutenticado
    }
