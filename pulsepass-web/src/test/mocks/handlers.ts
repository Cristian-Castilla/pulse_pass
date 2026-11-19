import { http, HttpResponse } from 'msw'
export const sede = {
  id: 1,
  code: 'VEN-SMR-01',
  name: 'Marina Convention Center',
  city: 'Santa Marta',
  address: 'Calle 1',
  capacity: 3,
  active: true,
}
export const artista = {
  id: 1,
  stageName: 'Solar Beat',
  country: 'Colombia',
  genre: 'Electrónica',
  active: true,
}
export const resumen = {
  id: 1,
  eventCode: 'CMF-2026',
  name: 'Caribbean Music Fest 2026',
  category: 'MUSIC',
  status: 'PUBLISHED',
  eventDate: '2026-12-12T20:00:00',
}
export const evento = {
  ...resumen,
  description: 'Una noche de música en Santa Marta.',
  minimumAge: 18,
  venueCode: sede.code,
  venueName: sede.name,
  artists: [artista],
}
export const usuario = {
  id: 1,
  username: 'andrea',
  email: 'andrea@pulsepass.test',
  active: true,
  firstName: 'Andrea',
  lastName: 'Ruiz',
  birthDate: '1998-04-12',
}
export const ticket = {
  id: 1,
  ticketCode: 'TKT-8F2A1C',
  type: 'VIP',
  price: 250000,
  status: 'PAID',
  purchaseDate: '2026-11-05T14:32:00',
  userEmail: usuario.email,
  userFullName: `${usuario.firstName} ${usuario.lastName}`,
  eventCode: evento.eventCode,
  eventName: evento.name,
}
export const error = (
  status: number,
  message = 'Solicitud rechazada',
  details: Record<string, string> = {},
) =>
  HttpResponse.json(
    {
      timestamp: '2026-11-18T12:00:00',
      status,
      error: 'Error',
      message,
      details,
    },
    { status },
  )
export const handlers = [
  http.get('/api/venues/active', () => HttpResponse.json([sede])),
  http.get('/api/venues/:code', () => HttpResponse.json(sede)),
  http.get('/api/artists/active', () => HttpResponse.json([artista])),
  http.get('/api/artists/by-stage-name', () => HttpResponse.json(artista)),
  http.get('/api/artists/:id', () => HttpResponse.json(artista)),
  http.get('/api/events/published', () => HttpResponse.json([resumen])),
  http.get('/api/events/by-artist', () => HttpResponse.json([resumen])),
  http.get('/api/events/:code/tickets/paid', () => HttpResponse.json([ticket])),
  http.get('/api/events/:code', () => HttpResponse.json(evento)),
  http.post('/api/events', async ({ request }) =>
    HttpResponse.json(
      { ...evento, ...((await request.json()) as object), status: 'DRAFT' },
      { status: 201 },
    ),
  ),
  http.patch('/api/events/:code/publish', () => HttpResponse.json(evento)),
  http.post('/api/events/:code/artists/:id', () => HttpResponse.json(evento)),
  http.post('/api/auth/login', () =>
    HttpResponse.json({
      accessToken: 'token-ficticio-de-pruebas',
      tokenType: 'Bearer',
      expiresIn: 900,
    }),
  ),
  http.get('/api/auth/me', () =>
    HttpResponse.json({
      id: usuario.id,
      username: usuario.username,
      email: usuario.email,
      active: usuario.active,
      roles: ['ROLE_USER'],
    }),
  ),
  http.post('/api/users', () => HttpResponse.json(usuario, { status: 201 })),
  http.get('/api/users/by-email', () => HttpResponse.json(usuario)),
  http.get('/api/users/by-username', () => HttpResponse.json(usuario)),
  http.get('/api/tickets/by-user', () => HttpResponse.json([ticket])),
  http.get('/api/tickets/:code', () => HttpResponse.json(ticket)),
  http.post('/api/tickets', () => HttpResponse.json(ticket, { status: 201 })),
  http.patch('/api/tickets/:code/cancel', () =>
    HttpResponse.json({ ...ticket, status: 'CANCELLED' }),
  ),
  http.patch('/api/tickets/:code/use', () =>
    HttpResponse.json({ ...ticket, status: 'USED' }),
  ),
]
