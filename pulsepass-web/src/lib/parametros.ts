import { z } from 'zod'
const sinControles = (v: string) =>
  ![...v].some((c) => c.charCodeAt(0) < 32 || c.charCodeAt(0) === 127)
export const CodigoSchema = z
  .string()
  .trim()
  .min(1, 'Ingresa un código')
  .max(200)
  .refine(
    (v) => sinControles(v) && !['/', '\\', '?', '#'].some((c) => v.includes(c)),
    'Código inválido',
  )
export const IdSchema = z.coerce
  .number()
  .int()
  .positive()
  .max(Number.MAX_SAFE_INTEGER)
export const BusquedaSchema = z.string().max(200)
export function destinoSeguro(valor: unknown) {
  const result = z.string().max(2000).safeParse(valor)
  return result.success &&
    result.data.startsWith('/') &&
    !result.data.startsWith('//') &&
    !result.data.includes('\\') &&
    sinControles(result.data) &&
    !/^\/(login|registro)([/?#]|$)/.test(result.data)
    ? result.data
    : '/'
}
