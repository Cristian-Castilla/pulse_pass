# Contratos y trazabilidad

## Fuente y decisiones cerradas

PRD frontend §8, §10, §21 y §29; contratos de `src/main/java/com/pulsepass/pass/controller` y `dto`. Base HEAD `9620087777a0efe8f7d853b218792a34fc4da966`, más la ampliación autorizada de perfil/titular en el árbol de trabajo, sin commit.

- No se modifica ningún PRD ni migración. Se amplían únicamente los DTO/mappers necesarios y la asociación del perfil recién registrado; no se cambian rutas ni permisos.
- Las 11 etapas se ejecutan sin paradas intermedias por autorización explícita. Commit y push siguen prohibidos.
- Entregar los datos del diseño con la ampliación autorizada; no inventar perfiles ni sustituir nombres con correos.
- Referencias visuales: objetos PNG incorporados en el PDF; se recuperaron sus 32 imágenes.
- La ciudad de las tarjetas sí puede obtenerse sin ampliar backend: resumen -> detalle de evento -> sede. Las consultas existentes y su caché se reutilizan; no se añaden endpoints.
- Roles wire `ROLE_*` -> roles de UI validados. Política del servidor: 403 prevalece; detalles de ticket/evento 403/404 se presentan como no disponibles sin revelar datos ajenos.

## Operaciones únicas

El registro es a la vez USR-01 y A3. Hay 22 operaciones únicas; no se inventa una vigesimotercera.

| ID          | Método y ruta (variables de referencia)                  | Punto de invocación en UI               |
| ----------- | -------------------------------------------------------- | --------------------------------------- |
| VEN-01      | `GET /api/venues/{{venueCode}}`                          | DetalleSede                             |
| VEN-02      | `GET /api/venues/active`                                 | ListaSedes / AdminCrearEvento           |
| EVT-01      | `POST /api/events`                                       | AdminCrearEvento                        |
| EVT-02      | `GET /api/events/{{eventCode}}`                          | DetalleEvento / ADMIN / tarjetas        |
| EVT-03      | `GET /api/events/published`                              | Inicio / CatalogoEventos                |
| EVT-04      | `PATCH /api/events/{{eventCode}}/publish`                | AdminGestionEvento                      |
| EVT-05      | `POST /api/events/{{eventCode}}/artists/{{artistId}}`    | AdminGestionEvento                      |
| EVT-06      | `GET /api/events/by-artist?stageName={{stageName}}`      | CatalogoEventos / DetalleArtista        |
| ART-01      | `GET /api/artists/{{artistId}}`                          | DetalleArtista                          |
| ART-02      | `GET /api/artists/by-stage-name?stageName={{stageName}}` | Filtro de artista en CatalogoEventos    |
| ART-03      | `GET /api/artists/active`                                | Inicio / ListaArtistas / ADMIN          |
| USR-01 / A3 | `POST /api/users`                                        | Registro                                |
| USR-02      | `GET /api/users/by-email?email={{email}}`                | Cuenta (identidad propia)               |
| USR-03      | `GET /api/users/by-username?username={{identifier}}`     | Cuenta (alternar consulta propia)       |
| TKT-01      | `POST /api/tickets`                                      | DetalleEvento / PanelCompra             |
| TKT-02      | `GET /api/tickets/{{ticketCode}}`                        | DetalleTicket / Confirmacion / STAFF    |
| TKT-03      | `GET /api/tickets/by-user?email={{email}}`               | MisTickets                              |
| TKT-04      | `GET /api/events/{{eventCode}}/tickets/paid`             | TicketsPagadosEvento (tabla y tarjetas) |
| TKT-05      | `PATCH /api/tickets/{{ticketCode}}/cancel`               | MisTickets / DetalleTicket              |
| TKT-06      | `PATCH /api/tickets/{{ticketCode}}/use`                  | ValidacionIngreso                       |
| A1          | `POST /api/auth/login`                                   | Login                                   |
| A2          | `GET /api/auth/me`                                       | AuthProvider / Cuenta                   |

Todos los adaptadores validan sus respuestas con zod. `src/test/api.test.ts` invoca los 22 contra MSW, registra solicitudes reales del cliente y comprueba que el registro no esté duplicado. Las pruebas de páginas verifican interacción; no confundir esa evidencia simulada con integración backend.

## DTO reales

- VenueResponse: id, code, name, city, address, capacity, active.
- ArtistResponse: id, stageName, country, genre, active.
- EventSummaryResponse: id, eventCode, name, category, status, eventDate. **No tiene ciudad ni sede**.
- EventResponse: id, eventCode, name, description, category, status, eventDate, minimumAge, venueCode, venueName, artists. La sede completa se obtiene mediante su endpoint.
- UserResponse: id, username, email, active, firstName, lastName, birthDate. Los tres campos de perfil admiten null para usuarios legados. Se conserva propietario o ADMIN; STAFF no obtiene perfiles ajenos.
- AuthenticatedUserResponse: id, username, email, active, roles.
- TicketResponse: id, ticketCode, type, price, status, purchaseDate, userEmail, eventCode, eventName, userFullName. El nombre es adicional, no identificador, y admite null si no existe perfil. No se incluye el resto del perfil.
- AuthResponse: accessToken, tokenType, expiresIn (segundos).
- ErrorResponse: timestamp, status, error, message, details.
- CreateEventRequest: eventCode, name, description (máximo 500), category, eventDate (LocalDateTime sin zona), minimumAge, venueCode.
- RegisterUserRequest: username, email, firstName, lastName, phone opcional, city opcional, birthDate, password. El formulario del PRD omite los dos opcionales.
- PurchaseTicketRequest: userEmail, eventCode, type. No se envía precio.
- LoginRequest: identifier, password.

## Ampliación autorizada y privacidad

PRD §9.10 y §10.2 requieren nombres, apellidos y nacimiento en Cuenta; §9.12, §9.14 y §9.15 requieren el titular del ticket. Las cuatro vistas consumen ahora esos campos. No hay endpoints nuevos ni consultas de perfiles ajenos desde STAFF.

La identidad de sesión conserva exclusivamente id, username, email, active y roles. El esquema de `/api/auth/me` excluye expresamente los campos personales del esquema de usuario. El correo sigue distinguiendo cuentas con el mismo nombre.

La autorización se comprueba antes del mapeo del perfil. Las respuestas de tickets solo incorporan el nombre, no nacimiento, teléfono ni ciudad. Se conserva la limitación del MVP: STAFF tiene alcance global en las operaciones de tickets, no asignación por evento.

Los campos nulos de registros legados se muestran como no informados; no se reutiliza el correo como nombre. Las claves nuevas son obligatorias: publicar primero el backend ampliado. No se certifica integración real por haber aprobado las pruebas simuladas.

## Caché y mutaciones

Query keys y tiempos siguen §14: publicados/artista 60 s, evento 30 s, artistas/sedes 5 min, usuario 60 s, tickets 15 s. Mutaciones sin reintento automático; 4xx sin reintentos de consulta; red/5xx un reintento.

Compra invalida tickets propios, evento, publicados y pagados del evento. Cancelación invalida tickets propios, detalle y pagados. Uso invalida detalle, pagados y tickets propios (evita que un ticket siga apareciendo PAID en la misma identidad combinada). Publicar/asociar invalidan evento y todas las listas de eventos: se cubren cartelera y búsquedas por artista, no solo la pantalla actual. Crear siembra el detalle con la respuesta validada y recuerda solo el código en sessionStorage.

## Límites conocidos

No hay listado global de eventos, disponibilidad restante, refresh, pagos, QR ni CRUD de artistas/sedes/usuarios/roles. No hay suite E2E, despliegue o CI/CD. No se han suministrado las tres guías React/Router/Tailwind mencionadas por el PRD; no se afirma haberlas leído. Las pruebas y la visualización de QA usan fixtures, jamás se presentan como respuestas reales de la API.
