import { beforeAll, afterAll, afterEach, it, expect } from 'vitest'
import { server } from './mocks/server'
import { authApi } from '../funcionalidades/auth/api'
import { artistasApi } from '../funcionalidades/artistas/api'
import { eventosApi } from '../funcionalidades/eventos/api'
import { sedesApi } from '../funcionalidades/sedes/api'
import { ticketsApi } from '../funcionalidades/tickets/api'
import { usuariosApi } from '../funcionalidades/usuarios/api'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
it('las 22 operaciones reales validan respuesta y usan método/ruta correctos', async () => {
  const recibidas: string[] = []
  const escuchar = ({ request }: { request: Request }) =>
    recibidas.push(request.method + ' ' + new URL(request.url).pathname)
  server.events.on('request:start', escuchar)
  try {
    await sedesApi.activas()
    await sedesApi.sede('VEN-SMR-01')
    await artistasApi.activos()
    await artistasApi.artista(1)
    await artistasApi.porNombre('Solar Beat')
    await eventosApi.publicados()
    await eventosApi.evento('CMF-2026')
    await eventosApi.porArtista('Solar Beat')
    await eventosApi.crear({
      eventCode: 'CMF-2026',
      name: 'Festival',
      category: 'MUSIC',
      eventDate: '2026-12-12T20:00:00',
      minimumAge: 18,
      venueCode: 'VEN-SMR-01',
    })
    await eventosApi.publicar('CMF-2026')
    await eventosApi.asociar('CMF-2026', 1)
    await authApi.login({
      identifier: 'andrea',
      password: 'contraseña-ficticia-de-prueba',
    })
    await authApi.me()
    await authApi.registrar({
      username: 'andrea',
      email: 'andrea@pulsepass.test',
      firstName: 'Andrea',
      lastName: 'Ruiz',
      birthDate: '1998-04-12',
      password: 'contraseña-ficticia-de-prueba',
    })
    await usuariosApi.porEmail('andrea@pulsepass.test')
    await usuariosApi.porUsername('andrea')
    await ticketsApi.comprar('andrea@pulsepass.test', 'CMF-2026', 'VIP')
    await ticketsApi.ticket('TKT-8F2A1C')
    await ticketsApi.porUsuario('andrea@pulsepass.test')
    await ticketsApi.pagados('CMF-2026')
    await ticketsApi.cancelar('TKT-8F2A1C')
    await ticketsApi.usar('TKT-8F2A1C')
    expect(new Set(recibidas).size).toBe(22)
    expect(recibidas).toContain('PATCH /api/tickets/TKT-8F2A1C/use')
    expect(recibidas).toContain('POST /api/events/CMF-2026/artists/1')
    expect(recibidas.filter((x) => x === 'POST /api/users')).toHaveLength(1)
  } finally {
    server.events.removeListener('request:start', escuchar)
  }
})
