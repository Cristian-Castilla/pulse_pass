import { z } from 'zod'
import { CodigoSchema } from '../../lib/parametros'
const Schema = z.array(CodigoSchema).max(200)
export function leerCreados() {
  try {
    const raw = sessionStorage.getItem('pulsepass.creados')
    const parsed = Schema.safeParse(raw ? JSON.parse(raw) : [])
    return parsed.success ? parsed.data : []
  } catch {
    return []
  }
}
export function recordarCreado(code: string) {
  try {
    sessionStorage.setItem(
      'pulsepass.creados',
      JSON.stringify(
        [...new Set([...leerCreados(), CodigoSchema.parse(code)])].slice(-200),
      ),
    )
  } catch {
    /* Sin storage sigue disponible la consulta por código. */
  }
}
