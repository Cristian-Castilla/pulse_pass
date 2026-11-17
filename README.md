# PulsePass

Plataforma de eventos, artistas y entradas para descubrir conciertos, festivales, conferencias y eventos culturales.

Caso de estudio académico de persistencia, servicios, controladores y seguridad.

## Stack tecnológico

- Java 21
- Spring Boot 4.x
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway (migraciones de esquema)
- Testcontainers (pruebas de integración)
- Maven

## Modelo de dominio

```
Venue 1 ---- N Event
Event N ---- M Artist
User 1 ---- 1 UserProfile
User 1 ---- N Ticket
Event 1 ---- N Ticket
```

### Entidades y atributos

| Entidad      | Atributos                                                                                   |
|--------------|---------------------------------------------------------------------------------------------|
| Venue        | id, code, name, city, address, capacity, active                                            |
| Event        | id, eventCode, name, description, category, status, eventDate, minimumAge, streamingUrl, venue |
| Artist       | id, stageName, country, genre, active                                                       |
| User         | id, username, email, active                                                                 |
| UserProfile  | id, firstName, lastName, phone, city, birthDate, user                                       |
| Ticket       | id, ticketCode, type, price, status, purchaseDate, user, event                              |

### Enums

| Enum          | Valores                                                      |
|---------------|--------------------------------------------------------------|
| EventCategory | MUSIC, SPORTS, TECHNOLOGY, EDUCATION, CULTURE, ENTERTAINMENT |
| EventStatus   | DRAFT, PUBLISHED, SOLD_OUT, CANCELLED, FINISHED              |
| TicketType    | GENERAL, VIP, BACKSTAGE, STUDENT                             |
| TicketStatus  | RESERVED, PAID, CANCELLED, USED                              |

## Migraciones Flyway

| Migración                           | Objetivo                                                               |
|-------------------------------------|------------------------------------------------------------------------|
| V1 - create_schema.sql              | Crea todas las tablas con PK, FK, UNIQUE, CHECK e índices              |
| V2 - insert_initial_artists.sql     | Inserta 5 artistas de prueba (Solar Beat, Neon Waves, Caribbean Sound, Ocean Drive, Digital Pulse) |
| V3 - add_streaming_url_to_event.sql | Agrega streaming_url VARCHAR(500) nullable a events sin modificar V1   |
| V4 - security_credentials_roles.sql | Credenciales/roles y event_date TIMESTAMP sin reescribir V1 |
| V5 - align_user_role_primary_key.sql | Alinea la PK de roles a (user_id, role), conservando sus datos |

## Consultas implementadas

### Query Methods (derivadas del nombre)

| Método | Repositorio | Descripción |
|--------|-------------|-------------|
| findByEventCode | EventRepository | Busca evento por código de negocio |
| findByStatusOrderByEventDateAsc | EventRepository | Eventos PUBLISHED ordenados por fecha |
| findByVenue_Code | EventRepository | Eventos de un venue por su código |
| findByCode | VenueRepository | Busca venue por código |
| findByStageName | ArtistRepository | Busca artista por nombre artístico |
| findByUsername | UserRepository | Busca usuario por username |
| findByEmailIgnoreCase | UserRepository | Busca usuario por email ignorando mayúsculas |
| findByTicketCode | TicketRepository | Busca ticket por código |
| existsByUsername / existsByEmail | UserRepository | Verifica existencia de identidad única |
| existsByTicketCode | TicketRepository | Verifica existencia de ticket |

### JPQL (consultas complejas con JOIN)

| Método | Repositorio | Descripción |
|--------|-------------|-------------|
| findByArtistStageName | EventRepository | Eventos donde participa un artista (N:M) |
| findByCityAndArtist | EventRepository | Eventos de una ciudad con un artista específico |
| findRecommendedEvents | EventRepository | Eventos publicados futuros por ciudad + artista (case-insensitive, DISTINCT) |
| findByUserEmail | TicketRepository | Tickets de un usuario por email (navega Ticket → User) |
| findByUserEmailAndStatus | TicketRepository | Tickets de un usuario filtrados por estado |
| findTicketsForFutureEvents | TicketRepository | Tickets cuyo evento es posterior a una fecha (orden cronológico) |
| findPaidTicketsByEventCode | TicketRepository | Tickets PAID de un evento por eventCode |
| countPaidTicketsByEventCode | TicketRepository | Conteo de tickets PAID de un evento |

> Las consultas que usan `JOIN FETCH` evitan el problema N+1 de Hibernate al navegar relaciones `@ManyToOne`.

## Ejecución de pruebas

```bash
mvn clean test
```

Las pruebas usan **Testcontainers** para levantar una instancia real de PostgreSQL durante la ejecución, garantizando que el esquema y las consultas funcionan contra el motor objetivo (no H2).

## Configuración

En `application.yml`:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/pulsepass}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Hibernate opera en modo **validate**: solo valida que el esquema coincida con las entidades, sin crear ni modificar tablas.

---

# Fase 4 — Capa de Seguridad

Autenticación con credenciales, emisión y validación de JWT (JJWT), autorización por roles,
verificación de propiedad de recursos y tratamiento uniforme de errores. La Fase 4 **no reemplaza**
las fases anteriores: añade un perímetro HTTP seguro sobre las 20 operaciones existentes.

## Stack de seguridad

| Componente | Tecnología |
|---|---|
| Framework | Spring Security 7.x (con Spring Boot 4.1.1) |
| JWT | JJWT 0.13.0 (HS256 explícito) |
| Hash de contraseña | BCrypt (fuerza por defecto del framework) |
| Roles | `ROLE_USER`, `ROLE_STAFF`, `ROLE_ADMIN` |
| Estado de sesión | Stateless (sin JSESSIONID) |

## Variables de entorno

Copiar `.env.example` a `.env.properties` y completar:

```bash
cp .env.example .env.properties
```

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL` | No (default `jdbc:postgresql://localhost:5432/pulsepass`) | Cadena de conexión |
| `DB_USER` | No (default `postgres`) | Usuario de la base |
| `DB_PASSWORD` | No (default `postgres`) | Contraseña de la base |
| `PULSEPASS_JWT_ISSUER` | No (default `pulsepass-api`) | Emisor del token (`iss`) |
| `PULSEPASS_JWT_SECRET_BASE64` | **Sí** | Clave HMAC en Base64 (mínimo 32 bytes) |
| `PULSEPASS_JWT_ACCESS_TOKEN_TTL` | No (default `PT15M`) | Vida del token (ISO-8601 Duration) |
| `PULSEPASS_CORS_ALLOWED_ORIGINS` | No (default vacío: ningún origen web autorizado) | Orígenes exactos separados por comas; sin `*`, rutas ni credenciales |

**El nombre del archivo importa.** Fuera del perfil `test`, `application.yml` importa `optional:file:.env.properties`, y
Spring Boot elige el loader de propiedades según la **extensión** del archivo. Un archivo llamado
`.env` (sin extensión conocida) no se carga: las variables quedan sin resolver y la aplicación
falla al arrancar con `pulsepass.security.jwt.secret-base64 is required`.

Generar una clave:

```bash
# PowerShell (generador criptográfico, compatible con Windows PowerShell)
$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()

# bash
head -c 48 /dev/urandom | base64
```

`.env.properties` está excluido del repositorio (`.gitignore`: `.env*`). **No subir la clave.**

## Roles

| Rol | Qué puede hacer |
|---|---|
| `USER` | Comprar entradas, consultar sus propios datos y tickets, cancelar sus propios tickets |
| `STAFF` | Consultar borradores y tickets operativos, listar PAID por evento y marcar tickets como usados; no administrar eventos ni cancelar tickets ajenos |
| `ADMIN` | Administrar eventos, asociar artistas y consultar datos operativos sensibles |

El rol inicial del registro público es **siempre** `USER`. No existe registro público con roles
privilegiados.

## Endpoints de autenticación

| Verbo y ruta | Acceso | Resultado |
|---|---|---|
| `POST /api/auth/login` | Público | `200` + `AuthResponse(accessToken, tokenType, expiresIn)` |
| `GET /api/auth/me` | Autenticado | `200` + `AuthenticatedUserResponse` |
| `POST /api/users` | Público | `201` + `UserResponse` (registro con contraseña) |

Ejemplo de login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier": "andrea", "password": "UnaFraseSeguraLarga2026!"}'
```

```json
{
  "accessToken": "<JWT_FIRMADO>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

## Matriz de autorización de las 20 operaciones

| ID | Método y ruta | Regla |
|---|---|---|
| VEN-01 | `GET /api/venues/{code}` | Público |
| VEN-02 | `GET /api/venues/active` | Público |
| EVT-01 | `POST /api/events` | ADMIN |
| EVT-02 | `GET /api/events/{eventCode}` | Público solo si PUBLISHED; STAFF/ADMIN ven borradores |
| EVT-03 | `GET /api/events/published` | Público |
| EVT-04 | `PATCH /api/events/{eventCode}/publish` | ADMIN |
| EVT-05 | `POST /api/events/{eventCode}/artists/{artistId}` | ADMIN |
| EVT-06 | `GET /api/events/by-artist` | Público solo PUBLISHED; STAFF/ADMIN ven borradores |
| ART-01 | `GET /api/artists/{id}` | Público |
| ART-02 | `GET /api/artists/by-stage-name` | Público |
| ART-03 | `GET /api/artists/active` | Público |
| USR-01 | `POST /api/users` | Público (rol inicial USER) |
| USR-02 | `GET /api/users/by-email` | Propio usuario, o ADMIN |
| USR-03 | `GET /api/users/by-username` | Propio usuario, o ADMIN |
| TKT-01 | `POST /api/tickets` | USER; `userEmail` debe pertenecer al principal |
| TKT-02 | `GET /api/tickets/{ticketCode}` | Propietario, o STAFF/ADMIN |
| TKT-03 | `GET /api/tickets/by-user` | Propietario, o ADMIN |
| TKT-04 | `GET /api/events/{eventCode}/tickets/paid` | STAFF/ADMIN |
| TKT-05 | `PATCH /api/tickets/{ticketCode}/cancel` | Propietario (USER) o ADMIN |
| TKT-06 | `PATCH /api/tickets/{ticketCode}/use` | STAFF/ADMIN |

## Propiedad de recursos (anti-IDOR)

La autorización de propiedad vive en `AuthorizationService`, no en el controlador: una invocación
directa al servicio tampoco puede operar sobre recursos ajenos.

- La compra valida que `userEmail` pertenezca al principal, comparando el **ID persistente** y no
  el string del correo.
- La consulta, el listado y la cancelación contrastan el propietario cargado desde la base con
  `principal.userId`; conocer el código del recurso no basta.
- La comparación es por ID persistente (PRD §9.1): el correo es editable, el id no.

## Contrato de errores

Los errores originados en el filtro (antes de `DispatcherServlet`) los escriben
`RestAuthenticationEntryPoint` y `RestAccessDeniedHandler`; el resto, `GlobalExceptionHandler`. Todos
devuelven el mismo `ErrorResponse`:

```json
{
  "timestamp": "2026-10-10T15:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "No tiene permisos para realizar esta operación",
  "details": {}
}
```

| Caso | HTTP | Responsable |
|---|---|---|
| Login incorrecto | 401 | `AuthController` / autenticación |
| Sin Bearer en ruta protegida | 401 | `AuthenticationEntryPoint` |
| JWT expirado o firmado incorrectamente | 401 | Filtro + `AuthenticationEntryPoint` |
| Usuario válido sin rol requerido | 403 | `AccessDeniedHandler` / `AuthorizationService` |
| Usuario intenta ticket ajeno | 403 | `AuthorizationService` |
| JSON / Bean Validation inválido | 400 | Handler previo |
| Usuario o evento no existente | 404 | Handler previo |
| Conflicto de reglas de compra/uso | 409 | Handler previo |
| Error inesperado | 500 | Handler previo; sin stack trace |

El mensaje de 401 es siempre el mismo: no distingue usuario inexistente de contraseña incorrecta,
para no permitir enumerar cuentas. El mensaje de 403 es uniforme y no revela si el recurso existe
ni quién es su dueño.

## Pruebas de seguridad

| Clase | Qué cubre |
|---|---|
| `JwtServiceTest` | Emisión y validación: firma, claims, expiración, issuer, manipulación, clave débil |
| `AuthServiceImplTest` | Login: credenciales correctas, contraseña incorrecta, usuario inactivo |
| `AuthorizationServiceImplTest` | Propiedad: dueño vs tercero, STAFF, ADMIN, rechazo de correos ajenos |
| `CustomUserDetailsServiceTest` | Carga de identidad y roles contra PostgreSQL |
| `SecurityCredentialsTest` | Persistencia de credenciales y roles (1:1, PK compuesta, rol restringido) |
| `LoginServiceTest` | Login contra servicios reales y base |
| `LoginHttpTest` | Login y cadena de filtros por HTTP (T-SEC-001..008, 016) |
| `AuthorizationMatrixTest` | Matriz de rol de las 20 operaciones |
| `OwnershipAntiIdorTest` | Anti-IDOR sobre compra, consulta, listado y cancelación |
| `DraftVisibilityTest` | Filtrado de eventos no publicados (EVT-02, EVT-06) |

Los tests **no deshabilitan los filtros** (`@AutoConfigureMockMvc(addFilters = false)`
invalidaría el propósito de probar seguridad). Las pruebas integradas usan Bearer real y PostgreSQL;
los cinco `*ControllerSliceTest` usan `@WebMvcTest`, `@MockitoBean` e identidad de prueba para aislar
el contrato HTTP, verificar las llamadas al Service y probar que una entrada rechazada no lo invoca.
`application-test.yml` aporta exclusivamente configuración ficticia y hereda persistencia del YAML
principal. El perfil `test` no importa el archivo local `.env.properties`; `@ServiceConnection`
aporta la base efímera y Hibernate debe mantener `ddl-auto=validate`.

## Migraciones de la Fase 4

| Migración | Objetivo |
|---|---|
| `V4__security_credentials_roles.sql` | Credenciales 1:1, roles restringidos con PK técnica y UNIQUE(user_id, role), y event_date TIMESTAMP |
| `V5__align_user_role_primary_key.sql` | Sustituye la PK técnica de roles por PK compuesta (user_id, role), sin cambiar V4 aplicada ni perder asignaciones |

`V1` declaraba `event_date DATE` y no se modificó: Flyway nunca reutiliza una versión aplicada. El
cambio de tipo se hizo con el `ALTER` de V4.

## Desviaciones del PRD

| ID | Divergencia | Resolución |
|---|---|---|
| D-02 | PRD §14.1 pide `spring-security-test`; con solo esa dependencia `@WithMockUser` no se auto-configura en Boot 4 | Se usa `spring-boot-starter-security-test` |
| D-03 | `io.jsonwebtoken` no lo gestiona el BOM de Boot 4.1.1 | Versión `0.13.0` explícita |
| D-04 | PRD de Servicios declara `eventDate` como `LocalDateTime`; el código tenía `LocalDate` | Se alineó el código en V4 |
| D-05 | Capacidad del venue inconsistente entre PRDs (5000 / 3 / 3) | Cada capa conserva lo suyo; la Fase 4 usa 5000 |
| D-06 | La Fase 3 dejó los endpoints públicos a propósito (no había autenticación) | La Fase 4 lo revierte explícitamente |
| D-07 | `RegisterUserRequest` no mencionaba `password` | Se extendió el contrato (cambio documentado) |
| D-08 | `ArtistRepository.findByStageName` vs `findByStageNameIgnoreCase` del PRD | Variación explícitamente permitida por el PRD |
| D-09 | El PRD lista `POST /api/users` como endpoint nuevo | Ya existía; solo se le extendió el request |
| D-10 | Promover a ADMIN por HTTP | Prohibido; los roles privilegiados se provisionan por proceso controlado |

## Limitaciones del MVP

El procedimiento de provisión privilegiada, activación de usuarios legados, CORS, request ID y el
gate HTTPS de despliegue se documentan en [operación segura](docs/operacion-segura.md).

- **STAFF global para inspección/ingreso.** El dominio no modela asignación de personal a eventos,
  así que puede inspeccionar tickets y marcar PAID como USED en cualquier evento. Esto no le permite
  leer datos personales ajenos, listar tickets por otro usuario ni cancelarlos.
- **Sin revocación individual.** Un token robado sigue válido hasta su vencimiento (15 minutos). No
  hay denylist de `jti`.
- **Sin límite de intentos de login** ni rotación automática de claves. Documentados como brechas,
  no como implementados.
