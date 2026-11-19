import { z } from 'zod'
export class ApiError extends Error {
  status: number
  details: Record<string, string>
  constructor(
    status: number,
    message: string,
    details: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}
const ErrorSchema = z.object({
  timestamp: z.string(),
  status: z.number(),
  error: z.string(),
  message: z.string(),
  details: z.record(z.string(), z.string()).nullish(),
})
let leerToken: () => string | null = () => null
let expirar: () => void = () => {}
export function configurarSesion(
  token: () => string | null,
  cerrar: () => void,
) {
  leerToken = token
  expirar = cerrar
}
export function tokenActual() {
  return leerToken()
}
interface Opciones {
  method?: 'GET' | 'POST' | 'PATCH'
  body?: unknown
  signal?: AbortSignal
  publica?: boolean
}
export async function http<T>(
  ruta: string,
  schema: z.ZodType<T>,
  opciones: Opciones = {},
): Promise<T> {
  const token = opciones.publica ? null : leerToken()
  const headers = new Headers({ Accept: 'application/json' })
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (opciones.body !== undefined)
    headers.set('Content-Type', 'application/json')
  const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
  let respuesta: Response
  try {
    respuesta = await fetch(`${base}${ruta}`, {
      method: opciones.method ?? 'GET',
      headers,
      body:
        opciones.body === undefined ? undefined : JSON.stringify(opciones.body),
      signal: opciones.signal,
      credentials: 'omit',
      redirect: 'error',
    })
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error
    throw new ApiError(0, 'No pudimos conectar con el servidor.')
  }
  // Una respuesta de una identidad anterior nunca vuelve a poblar la sesión ni la caché.
  if (token && token !== leerToken())
    throw new DOMException('Sesión reemplazada', 'AbortError')
  const body: unknown = await respuesta.json().catch(() => null)
  if (token && token !== leerToken())
    throw new DOMException('Sesión reemplazada', 'AbortError')
  if (!respuesta.ok) {
    const parsed = ErrorSchema.safeParse(body)
    const status = respuesta.status
    if (status === 401 && token) expirar()
    const seguro =
      status === 401
        ? opciones.publica
          ? 'Credenciales inválidas'
          : 'Tu sesión expiró'
        : status === 403
          ? 'No tiene permisos para realizar esta operación'
          : status === 404
            ? 'Recurso no disponible'
            : status >= 500
              ? 'Ocurrió un error inesperado. Inténtalo más tarde.'
              : parsed.success
                ? parsed.data.message
                : 'No se pudo completar la solicitud.'
    throw new ApiError(
      status,
      seguro,
      status === 400 && parsed.success ? (parsed.data.details ?? {}) : {},
    )
  }
  const parsed = schema.safeParse(body)
  if (!parsed.success)
    throw new ApiError(
      502,
      'La respuesta del servidor no cumple el contrato esperado.',
    )
  return parsed.data
}
