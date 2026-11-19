# Checklist de Ejecución Manual — PulsePass E2E

**Propósito:** Servir como guía paso a paso para la ejecución manual de las pruebas E2E.
**Precondición:** Base de datos con el seed aplicado y el servidor Spring Boot corriendo en `http://localhost:8080`.

---

## Bloque 1: Autenticación (Carpeta 03_Auth / usuarios)

| ID | Acción / Petición | Actor | Resultado Esperado | Comprobación Manual |
| :--- | :--- | :--- | :--- | :--- |
| **TC-AUTH-001** | `POST /api/users`<br>Body: Registrar USER_A | Anónimo | `201 Created` | Verificar que el JSON de respuesta **NO** contenga `password`. El rol debe ser `USER`. |
| **TC-AUTH-002** | `POST /api/auth/login`<br>Body: username y password de USER_A | Anónimo | `200 OK` | Copiar el `accessToken` a la variable `{{userAToken}}`. |
| **TC-AUTH-003** | `POST /api/auth/login`<br>Body: email y password de USER_A | Anónimo | `200 OK` | Verificar que el ID/username retornado sea el mismo que en TC-AUTH-002. |
| **TC-AUTH-004** | `POST /api/auth/login`<br>Body: password incorrecta | Anónimo | `401 Unauthorized` | Verificar que el mensaje de error sea genérico (no debe decir "contraseña incorrecta"). |
| **TC-AUTH-005** | `POST /api/auth/login`<br>Body: usuario inexistente | Anónimo | `401 Unauthorized` | El mensaje debe ser **idéntico** al de TC-AUTH-004 (protección anti-enumeración). |
| **TC-AUTH-006** | `GET /api/auth/me`<br>Auth: `{{adminToken}}` | Admin | `200 OK` | Verificar identidad. No debe haber campos de contraseña ni `passwordHash`. |
| **TC-AUTH-007** | `GET /api/auth/me`<br>Auth: (Ninguna) | Anónimo | `401 Unauthorized` | El JSON debe tener los 5 campos de `ErrorResponse`. |
| **TC-AUTH-008** | `GET /api/auth/me`<br>Auth: `Bearer abc.def.ghi` | Anónimo | `401 Unauthorized` | Token inventado debe ser rechazado. |
| **TC-AUTH-009** | `GET /api/auth/me`<br>Auth: Token válido pero alterado (1 carácter) | Anónimo | `401 Unauthorized` | El filtro debe cortar la petición antes del controlador. |
| **TC-AUTH-010** | JWT expirado | - | - | **[BLOCKED]** Requiere token de corta duración generado por mecanismo test. |
| **TC-AUTH-011** | JWT firmado por otra clave | - | - | **[BLOCKED]** Requiere construcción offline con clave test. |
| **TC-AUTH-012** | `GET /api/auth/me?token={{adminToken}}` | Anónimo | `401 Unauthorized` | El servidor no debe aceptar tokens por query string. |
| **TC-AUTH-013** | Login con `e2e_inactive` y posterior `/auth/me` | Inactivo | `401` | La cuenta inactiva no debe poder autenticarse. |
| **TC-AUTH-014** | `POST /api/events` (Body vacío) | USER_A | `403 Forbidden` | Token válido, pero rol insuficiente. |
| **TC-AUTH-015** | `GET /api/venues/{{venueActiveCode}}`<br>Auth: Token inválido | Anónimo | `200 OK` | Ruta pública debe ignorar el token inválido y devolver el recurso. |

---

## Bloque 2: Seguridad y Propiedad (Carpeta 07_Seguridad)

| ID | Acción / Petición | Actor | Resultado Esperado | Comprobación Manual |
| :--- | :--- | :--- | :--- | :--- |
| **TC-SEC-001** | `POST /api/events` | Anónimo / USER | `401` / `403` | Verificar que Anónimo reciba 401 y USER reciba 403. |
| **TC-SEC-002** | `PATCH /api/events/.../publish` | STAFF / USER | `403 Forbidden` | Ninguno de estos roles puede publicar. |
| **TC-SEC-003** | `POST /api/events/.../artists/...` | USER_A | `403 Forbidden` | USER no asocia artistas. |
| **TC-SEC-004** | `GET /api/events/borrador-falso` | Anónimo | `403` / `404` | No debe retornar datos de eventos en DRAFT. |
| **TC-SEC-005** | `GET /api/events/by-artist...` | Anónimo | `200 OK` | Verificar que en el JSON **NO** aparezcan eventos en estado `DRAFT`. |
| **TC-SEC-006** | `GET /api/users/by-email?email={{userAEmail}}` | USER_B | `403` / `404` | **IDOR:** USER_B no debe ver los datos de USER_A. |
| **TC-SEC-007** | `GET /api/users/by-username?username={{userAUsername}}` | USER_B | `403` / `404` | **IDOR:** USER_B no debe ver los datos de USER_A. |
| **TC-SEC-008** | `POST /api/tickets` (Body con email de B) | USER_A | `403` / `400` | USER_A no puede comprar usando la identidad de B. |
| **TC-SEC-009** | `GET /api/tickets/TKT-FALSO` | USER_B | `403` / `404` | Bloqueo de lectura de tickets ajenos. |
| **TC-SEC-010** | `GET /api/tickets/by-user?email={{userAEmail}}` | USER_B | `403` / `404` | Bloqueo de listado de tickets ajenos. |
| **TC-SEC-011** | `PATCH /api/tickets/TKT-FALSO/cancel` | USER_B | `403` / `404` | Bloqueo de cancelación de tickets ajenos. |
| **TC-SEC-012** | `PATCH /api/tickets/TKT-FALSO/use` | USER_B | `403` | Bloqueo de uso de tickets ajenos. |
| **TC-SEC-013** | `GET /api/events/.../tickets/paid` | USER_A | `403 Forbidden` | Un USER no puede ver tickets pagados. |
| **TC-SEC-014** | `PATCH /api/tickets/TKT-FALSO/use` | STAFF | `404 Not Found` | Pasa el filtro de seguridad (no da 403), pero el ticket no existe. |
| **TC-SEC-015** | `GET /api/events/CUALQUIERA` | ADMIN | `404 Not Found` | Pasa el filtro de seguridad, pero el evento no existe. |
| **TC-SEC-016** | `POST /api/users` (Body con `"role":"ADMIN"`) | Anónimo | `201 Created` | Verificar que el rol real en la respuesta sea `USER`, no `ADMIN`. |

---

## Bloque 3: Flujo de Negocio y Transiciones (Carpeta 09_Flujos)

*Este bloque debe ejecutarse en orden estricto, ya que depende del estado de las peticiones anteriores.*

| ID | Acción / Petición | Actor | Resultado Esperado | Comprobación Manual |
| :--- | :--- | :--- | :--- | :--- |
| **TC-BIZ-001** | `GET /api/venues/active` y `/venues/{code}` | Anónimo | `200 OK` | Verificar que esté `VEN-E2E-01`. |
| **TC-BIZ-002** | `GET /api/artists/active` y `/artists/{id}` | Anónimo | `200 OK` | Verificar que esté `Artista E2E`. |
| **TC-BIZ-003** | `POST /api/events` (Body válido) | ADMIN | `201 Created` | Guardar el `eventCode` devuelto (ej. `EVT-R1-001`). |
| **TC-BIZ-004** | `PATCH /api/events/{{eventCode}}/publish` | ADMIN | `200 OK` | Hacer `GET /events/published` y verificar que el evento aparezca. |
| **TC-BIZ-005** | `POST /api/events/{{eventCode}}/artists/{{artistId}}` | ADMIN | `200` / `201` | Hacer `GET /events/by-artist` y verificar que el evento esté asociado. |
| **TC-BIZ-006** | `POST /api/users` (Crear USER_A y B) | Anónimo | `201` / `409` | Verificar que el intento de duplicado devuelva 409. |
| **TC-BIZ-007** | `POST /api/tickets`<br>Body: `userEmail`, `eventCode`, `type: GENERAL` | USER_A | `201 Created` | Guardar el `ticketCode` devuelto. |
| **TC-BIZ-008** | `GET /api/tickets/{{ticketCode}}` | USER_A / USER_B | `200` / `403` | USER_A lee su ticket. USER_B recibe 403/404. |
| **TC-BIZ-009** | `GET /api/events/{{eventCode}}/tickets/paid` | STAFF | `200 OK` | Verificar que el ticket de USER_A esté en la lista. |
| **TC-BIZ-010** | `PATCH /api/tickets/{{ticketCode}}/cancel` | USER_A | `200 OK` | Verificar que el ticket ahora sea `CANCELLED`. Repetir la petición debe dar `409 Conflict`. |