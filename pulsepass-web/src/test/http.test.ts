import { describe, it, expect, afterEach, vi } from 'vitest'
import { z } from 'zod'
import { http, ApiError, configurarSesion } from '../lib/http'
import { precioCOP } from '../lib/formato'
afterEach(() =>
  configurarSesion(
    () => null,
    () => {},
  ),
)
describe('Cliente HTTP', () => {
  it('TEST-FE-LIB-001 Bearer solo en el encabezado', async () => {
    configurarSesion(
      () => 'token-ficticio',
      () => {},
    )
    const mock = vi.fn().mockResolvedValue(Response.json({ ok: true }))
    vi.stubGlobal('fetch', mock)
    await http('/test', z.object({ ok: z.boolean() }))
    expect(mock.mock.calls[0]?.[0]).toBe('/api/test')
    expect(
      new Headers(mock.mock.calls[0]?.[1].headers).get('Authorization'),
    ).toBe('Bearer token-ficticio')
  })
  it('TEST-FE-LIB-002 conserva status y details válidos', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        Response.json(
          {
            timestamp: 'x',
            status: 400,
            error: 'Bad Request',
            message: 'Inválido',
            details: { name: 'Requerido' },
          },
          { status: 400 },
        ),
      ),
    )
    await expect(http('/test', z.unknown())).rejects.toMatchObject({
      status: 400,
      details: { name: 'Requerido' },
    })
  })
  it('TEST-FE-LIB-003 cuerpo inválido produce error seguro', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response('<html>stack privado</html>', { status: 500 }),
        ),
    )
    await expect(http('/test', z.unknown())).rejects.toMatchObject({
      status: 500,
      message: 'Ocurrió un error inesperado. Inténtalo más tarde.',
    })
  })
  it('TEST-FE-LIB-004 un 401 autenticado notifica expiración', async () => {
    const cerrar = vi.fn()
    configurarSesion(() => 'token', cerrar)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('', { status: 401 })),
    )
    await expect(http('/test', z.unknown())).rejects.toBeInstanceOf(ApiError)
    expect(cerrar).toHaveBeenCalledOnce()
  })
  it('401 de login no notifica expiración', async () => {
    const cerrar = vi.fn()
    configurarSesion(() => 'token', cerrar)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('', { status: 401 })),
    )
    await expect(
      http('/auth/login', z.unknown(), { publica: true }),
    ).rejects.toMatchObject({ message: 'Credenciales inválidas' })
    expect(cerrar).not.toHaveBeenCalled()
  })
  it('rechaza datos exitosos fuera del contrato', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(Response.json({ ok: 'si' })),
    )
    await expect(
      http('/test', z.object({ ok: z.boolean() })),
    ).rejects.toMatchObject({ status: 502 })
  })
  it('TEST-FE-LIB-005 formatea COP con Intl', () => {
    expect(precioCOP(250000)).toContain('250.000')
  })
})
