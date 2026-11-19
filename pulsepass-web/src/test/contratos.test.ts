import { describe, it, expect } from 'vitest'
import {
  RegistroSchema,
  UsuarioAutenticadoSchema,
} from '../funcionalidades/auth/tipos'
import {
  CrearEventoSchema,
  EventoResumenSchema,
} from '../funcionalidades/eventos/tipos'
describe('Contratos reales de PulsePass', () => {
  it('rechaza contraseñas de menos de 12 y de más de 72 bytes UTF-8', () => {
    const base = {
      username: 'andrea',
      email: 'andrea@example.test',
      firstName: 'Andrea',
      lastName: 'Ruiz',
      birthDate: '1998-04-12',
    }
    expect(
      RegistroSchema.safeParse({ ...base, password: '12345678901' }).success,
    ).toBe(false)
    expect(
      RegistroSchema.safeParse({ ...base, password: 'ñ'.repeat(37) }).success,
    ).toBe(false)
    expect(
      RegistroSchema.safeParse({ ...base, password: 'UnaFraseLarga2026!' })
        .success,
    ).toBe(true)
  })
  it('no admite fechas imposibles o futuras al registrarse', () => {
    const base = {
      username: 'a',
      email: 'a@example.test',
      firstName: 'A',
      lastName: 'B',
      password: 'UnaFraseLarga2026!',
    }
    expect(
      RegistroSchema.safeParse({ ...base, birthDate: '2025-02-30' }).success,
    ).toBe(false)
    expect(
      RegistroSchema.safeParse({ ...base, birthDate: '9999-01-01' }).success,
    ).toBe(false)
  })
  it('normaliza únicamente roles válidos recibidos del servidor', () => {
    expect(
      UsuarioAutenticadoSchema.parse({
        id: 1,
        username: 'a',
        email: 'a@example.test',
        active: true,
        roles: ['ROLE_USER', 'ROLE_ADMIN'],
      }).roles,
    ).toEqual(['USER', 'ADMIN'])
    expect(
      UsuarioAutenticadoSchema.safeParse({
        id: 1,
        username: 'a',
        email: 'a@example.test',
        active: true,
        roles: ['OWNER'],
      }).success,
    ).toBe(false)
  })
  it('valida resumen sin inventar ciudad o sede', () => {
    expect(
      EventoResumenSchema.parse({
        id: 1,
        eventCode: 'CMF-2026',
        name: 'Festival',
        category: 'MUSIC',
        status: 'PUBLISHED',
        eventDate: '2026-12-12T20:00:00',
      }),
    ).not.toHaveProperty('city')
  })
  it('valida descripción máxima, tipos y edad estructural', () => {
    const base = {
      eventCode: 'CMF-2026',
      name: 'Festival',
      category: 'MUSIC',
      eventDate: '2026-12-12T20:00',
      minimumAge: 0,
      venueCode: 'VEN-SMR-01',
    }
    expect(CrearEventoSchema.safeParse(base).success).toBe(true)
    expect(
      CrearEventoSchema.safeParse({ ...base, description: 'x'.repeat(501) })
        .success,
    ).toBe(false)
    expect(
      CrearEventoSchema.safeParse({ ...base, minimumAge: -1 }).success,
    ).toBe(false)
  })
})
