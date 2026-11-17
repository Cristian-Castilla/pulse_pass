# Matriz de Autorización — contrato vigente y antecedentes de Etapa 1

La base/fecha siguientes identifican el levantamiento histórico. Los permisos son los del PRD §11;
el cierre de correcciones y la ejecución actual se registran en `Correcciones_Auditoria_PulsePass.md`.

**Proyecto:** PulsePass · **Rama:** `feature/pulsepass-security` · **Base:** `6cc4233`
**Fuente:** PRD de Seguridad §11 (matriz) y §14.3 (fragmento de `SecurityConfig`)
**Método:** lectura de los 5 `@RestController` reales + cruce contra el PRD. Sin escribir código.
**Fecha:** 2026-10-17

---

## 1. Matriz de las 20 operaciones

Columnas: operación del PRD · verbo y ruta · clase dueña · método de Service que invoca · regla de rol Fase 4 · nota de propiedad.

| ID | Método y ruta | Clase dueña | Service invocado | Regla Fase 4 | Propiedad |
|---|---|---|---|---|---|
| VEN-01 | `GET /api/venues/{code}` | `VenueController` | `VenueService.findByCode` | Público | — |
| VEN-02 | `GET /api/venues/active` | `VenueController` | `VenueService.findActiveVenues` | Público | — |
| EVT-01 | `POST /api/events` | `EventController` | `EventService.create` | ADMIN | — |
| EVT-02 | `GET /api/events/{eventCode}` | `EventController` | `EventService.findByCode` | Público **si PUBLISHED**; STAFF/ADMIN si no | — |
| EVT-03 | `GET /api/events/published` | `EventController` | `EventService.findPublishedEvents` | Público | — |
| EVT-04 | `PATCH /api/events/{eventCode}/publish` | `EventController` | `EventService.publish` | ADMIN | — |
| EVT-05 | `POST /api/events/{eventCode}/artists/{artistId}` | `EventController` | `EventService.addArtist` | ADMIN | — |
| EVT-06 | `GET /api/events/by-artist?stageName=` | `EventController` | `EventService.findByArtist` | Público **solo eventos publicados** | — |
| ART-01 | `GET /api/artists/{id}` | `ArtistController` | `ArtistService.findById` | Público | — |
| ART-02 | `GET /api/artists/by-stage-name?stageName=` | `ArtistController` | `ArtistService.findByStageName` | Público | — |
| ART-03 | `GET /api/artists/active` | `ArtistController` | `ArtistService.findActiveArtists` | Público | — |
| USR-01 | `POST /api/users` | `UserController` | `AuthService.register` → `UserService.register` | Público (rol inicial solo USER) | — |
| USR-02 | `GET /api/users/by-email?email=` | `UserController` | `UserService.findByEmail` | Propio o ADMIN | sí |
| USR-03 | `GET /api/users/by-username?username=` | `UserController` | `UserService.findByUsername` | Propio o ADMIN | sí |
| TKT-01 | `POST /api/tickets` | `TicketController` | `TicketService.purchase` | USER; `userEmail` = principal | sí |
| TKT-02 | `GET /api/tickets/{ticketCode}` | `TicketController` | `TicketService.findByCode` | Propietario, o STAFF/ADMIN | sí |
| TKT-03 | `GET /api/tickets/by-user?email=` | `TicketController` | `TicketService.findByUserEmail` | Propietario, o ADMIN | sí |
| TKT-04 | `GET /api/events/{eventCode}/tickets/paid` | `EventController` | `TicketService.findPaidTicketsByEvent` | STAFF/ADMIN | — |
| TKT-05 | `PATCH /api/tickets/{ticketCode}/cancel` | `TicketController` | `TicketService.cancel` | Propietario (USER) o ADMIN | sí |
| TKT-06 | `PATCH /api/tickets/{ticketCode}/use` | `TicketController` | `TicketService.markAsUsed` | STAFF/ADMIN | — |

**Total: 20 operaciones.** Coincide con el PRD §11 ("las cinco tablas de controlador suman 20 operaciones") y con los 20 `@RequestMapping` del código.

### Distribución real por clase (difiere de la agrupación del PRD)

El PRD agrupa por **recurso**; el código agrupa por **clase controladora**. Verificado:

| Clase | Endpoints | Qué contiene |
|---|---|---|
| `EventController` | **7** | 6 de `EventService` + `TKT-04` (que es de `TicketService`) |
| `TicketController` | **5** | 5 de `TicketService` |
| `VenueController` | 2 | |
| `ArtistController` | 3 | |
| `UserController` | 3 | |

7 + 5 + 2 + 3 + 3 = **20** ✓

**Punto de atención (trampa de "dueño"):** `TKT-04` (`GET /api/events/{eventCode}/tickets/paid`)
está en el PRD dentro de la tabla de Ticket pero vive en `EventController` (`EventController.java:69-73`)
porque es un sub-recurso de `/api/events`. Es correcto: el controlador inyecta `TicketService` además de
`EventService` (constructor con dos dependencias). No se mueve.

---

## 2. Orden de matchers en `SecurityConfig`

En `authorizeHttpRequests` de Spring Security, **el primer patrón que matchea gana** (no como en Spring MVC,
donde el literal siempre gana por ranking). Hay 4 grupos donde un patrón con variable se tragaría a un
literal si se declara antes:

| Grupo | Literal que debe ir ANTES | Patrón con variable que lo tragaría |
|---|---|---|
| Events | `GET /api/events/published` y `GET /api/events/by-artist` | `GET /api/events/{eventCode}` |
| Tickets | `GET /api/tickets/by-user` | `GET /api/tickets/{ticketCode}` |
| Artists | `GET /api/artists/by-stage-name` y `GET /api/artists/active` | `GET /api/artists/{id}` |
| Venues | `GET /api/venues/active` | `GET /api/venues/{code}` |

Sin el orden correcto, `/api/events/published` se matchearía como `eventCode = "published"` y caería en la
regla condicional de EVT-02. Es exactamente la advertencia 4 del PRD §14.3.

**Nota:** el fragmento de ejemplo del PRD §14.3 resuelve events/artistas/venues con `permitAll` y
`/**`, pero **no** resuelve tickets (usa solo el literal `PATCH /api/tickets/*/use`), por lo que
`GET /api/tickets/by-user` queda cubierto por `anyRequest().authenticated()` — correcto como fallback,
pero el orden literal-antes-de-variable hay que declararlo explícitamente en la Etapa 6.

---

## 3. Discrepancias: PRD §11 vs código

**Ninguna en rutas, verbos ni dueños.** Las 20 operaciones del PRD existen en el código con el mismo
verbo, la misma ruta y la misma clase. No hay endpoint huérfano ni dueño equivocado.

Única salvedad ya documentada (D-09): el PRD §10.1 lista `POST /api/users` dentro de "Nuevos endpoints de
autenticación", pero ya existe (`UserController.java:26`). No se crea duplicado.

---

## 4. Discrepancias: PRD §14.3 (fragmento de `SecurityConfig`) vs matriz

El fragmento de ejemplo **no es la matriz completa** — el propio PRD lo dice en la advertencia 1.
Estas son las diferencias concretas que hay que resolver en la Etapa 6:

| # | Hallazgo | Impacto |
|---|---|---|
| 1 | El ejemplo no contempla `GET /api/events/{eventCode}` (EVT-02). Cae en `anyRequest().authenticated()`, así que un anónimo **no** podría leer un evento publicado por su código. | Contradice §11 EVT-02 ("Público solo si publicado"). Requiere decisión D3. |
| 2 | El ejemplo no contempla `GET /api/events/by-artist` (EVT-06). Idem: cae en `authenticated()`. | Contradice §11 EVT-06. Requiere decisión D3. |
| 3 | Los endpoints de propiedad (USR-02, USR-03, TKT-01, TKT-02, TKT-03, TKT-05) no aparecen en el ejemplo. Caen en `anyRequest().authenticated()`. | Correcto como piso mínimo, pero la verificación de propiedad es de servicio, no del filtro. |
| 4 | El ejemplo usa `PATCH /api/tickets/*/use` (un asterisco) para TKT-06, pero no declara el literal `GET /api/tickets/by-user` antes de `GET /api/tickets/{ticketCode}`. | Ver §2. |
| 5 | El ejemplo no incluye `/api/auth/login` ni `/api/auth/me`, que son nuevos en la Fase 4. | Se agregan en la Etapa 4/6. |

**Conclusión:** el ejemplo del §14.3 es un punto de partida, no la matriz. La matriz real es la de §11
y es la que manda (PRD §20: "El spec es la fuente de verdad").

---

## 5. Decisiones resueltas

| ID | Resolución vigente |
|---|---|
| **D1** | Recursos ajenos: 403 genérico. Un borrador no visible se oculta como 404. |
| **D2** | Tests crean credenciales/roles ficticios explícitamente; no hay contraseñas ni ADMIN predeterminados en Flyway. Operación manual en `docs/operacion-segura.md`. |
| **D3** | Los servicios existentes filtran visibilidad: anónimo/USER solo PUBLISHED; STAFF/ADMIN pueden consultar otros estados. Ambas rutas HTTP son alcanzables sin token, sin exponer borradores. |

Las diferencias del fragmento didáctico de §4 son antecedentes, no decisiones abiertas. El filtro
exige USER para comprar y el servicio exige además propiedad por ID; ADMIN no hereda USER.

---

## 6. Confirmaciones del PRD que esta etapa deja verificadas

- [x] Las 20 rutas existen con verbo y clase correctos (ver §1).
- [x] `GET /api/venues/{code}` y `GET /api/venues/active` quedan públicas (ver §1).
- [x] El orden de matchers importa y está identificado (ver §2).
- [x] `TKT-04` es de Ticket pero vive en `EventController` (ver §1).
- [x] No hay endpoint del PRD sin implementar ni implementado sin estar en el PRD.
- [x] Prueba HTTP explícita de `GET /api/venues/active` en VenueControllerTest y contrato aislado en VenueControllerSliceTest.
