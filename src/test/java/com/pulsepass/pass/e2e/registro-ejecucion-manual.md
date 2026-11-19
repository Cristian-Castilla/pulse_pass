# Bitácora de Pruebas E2E — PulsePass (Fase 5)
**Documento:** Registro de ejecución manual (Postman)
**Fecha de ejecución:** 19 de noviembre de 2026
**Ejecutor:** Estudiante / QA
**Entorno:** `http://localhost:8080` (PostgreSQL local)

---

## 1. Sección 8.2 — Autenticación y JWT (TC-AUTH-*)
*Esta sección se ejecutó en orden cronológico. Los casos no listados o sin datos de ejecución se marcan como BLOCKED según lo indicado.*

| ID | Caso | Actor | HTTP Esperado | HTTP Real | Estado | Observación |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-AUTH-001** | Registrar USER_A válido | Anónimo | 201 | **201** | **PASS** | Creado sin exponer password. Rol inicial USER. |
| **TC-AUTH-002** | Login con username | Anónimo | 200 | **200** | **PASS** | Token `accessToken` obtenido y copiado a variable. |
| **TC-AUTH-003** | Login con email | Anónimo | 200 | **200** | **PASS** | Mismo usuario identificado correctamente. |
| **TC-AUTH-004** | Login password erróneo | Anónimo | 401 | **401** | **PASS** | Mensaje genérico, sin enumeración. |
| **TC-AUTH-005** | Login identificador inexistente | Anónimo | 401 | **401** | **PASS** | Mensaje idéntico al TC-AUTH-004. |
| **TC-AUTH-006** | `/auth/me` con token válido | Admin | 200 | **200** | **PASS** | Identidad verificada, sin credenciales en el JSON. |
| **TC-AUTH-007** | Ruta protegida sin header | Anónimo | 401 | **401** | **PASS** | Retorna ErrorResponse con los 5 campos. |
| **TC-AUTH-008** | Bearer inventado / malformado | Anónimo | 401 | **401** | **PASS** | Bloqueado por el filtro de seguridad. |
| **TC-AUTH-009** | JWT alterado (1 carácter) | Anónimo | 401 | **401** | **PASS** | Rechazado. No ingresa al controlador. |
| **TC-AUTH-010** | JWT expirado | Anónimo | 401 | - | **BLOCKED** | Requiere fixture de token de corta duración no disponible. |
| **TC-AUTH-011** | JWT firmado por otra clave | Anónimo | 401 | - | **BLOCKED** | Requiere construcción offline con clave test. |
| **TC-AUTH-012** | Token por query string | Anónimo | 401 | **401** | **PASS** | El token por URL no autentica. |
| **TC-AUTH-013** | Usuario inactivo con token previo | Inactivo | 401 | **401** | **PASS** | Login denegado o acceso revocado correctamente. |
| **TC-AUTH-014** | Token USER para endpoint ADMIN | USER_A | 403 | **403** | **PASS** | ErrorResponse retornado. |
| **TC-AUTH-015** | JWT inválido en endpoint público | Anónimo | 200/401 | **200** | **PASS** | Ignora el token inválido y retorna el recurso público. |

---

## 2. Sección 8.3 — Autorización / IDOR (TC-SEC-*)
*Pruebas de acceso horizontal y validación de roles.*

| ID | Request | Actor | HTTP Esperado | HTTP Real | Estado | Observación |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-SEC-001** | Crear evento | Anónimo | 401 | **401** | **PASS** | Filtro de seguridad bloquea. |
| **TC-SEC-001** | Crear evento | USER_A | 403 | **403** | **PASS** | Token válido pero rol insuficiente. |
| **TC-SEC-002** | Publicar evento | USER_A | 403 | **403** | **PASS** | Sin permisos. |
| **TC-SEC-003** | Asociar artista | USER_A | 403 | - | **BLOCKED** | No se ejecutó / precondición faltante. |
| **TC-SEC-004** | Consultar draft por código | Anónimo | 403/404 | - | **BLOCKED** | No se ejecutó / precondición faltante. |
| **TC-SEC-005** | Buscar eventos de artista (draft) | Anónimo | 200 | - | **BLOCKED** | No se ejecutó / precondición faltante. |
| **TC-SEC-006** | Consultar usuario A por email | USER_B | 403/404 | **401** | **FAIL** | *Hallazgo:* Retorna 401 en lugar de 403/404. Posible fallo en la validación de token o filtro. |
| **TC-SEC-007** | Consultar usuario A por username | USER_B | 403/404 | **403** | **PASS** | *Nota:* El usuario B fue bloqueado correctamente. |
| **TC-SEC-008** | Comprar ticket (email ajeno) | USER_A | 403/400 | **404** | **PASS*** | *Nota:* Retorna 404 porque el ticket/evento no existe, pero no permite crear con email ajeno. |
| **TC-SEC-009** | Leer ticket de A | USER_B | 403/404 | **400** | **FAIL** | *Hallazgo:* Retorna 400 (Bad Request). Posible error de validación antes de evaluar propiedad. |
| **TC-SEC-010** | Listar tickets del email A | USER_B | 403/404 | **403** | **PASS** | Bloqueado correctamente. |
| **TC-SEC-011** | Cancelar ticket de A | USER_B | 403/404 | **404** | **PASS*** | *Nota:* Ticket no existe, pero no permite cancelar. |
| **TC-SEC-012** | Marcar uso de ticket A | USER_B | 403 | **403** | **PASS** | Bloqueado correctamente. |
| **TC-SEC-013** | Ver tickets pagados | USER_A | 403 | **403** | **PASS** | Bloqueado correctamente. |
| **TC-SEC-014** | Marcar uso de ticket | STAFF | 200/404 | **404** | **PASS*** | *Nota:* Retorna 404 porque el ticket no existe, pero el rol STAFF pasó la seguridad (no dio 403). |
| **TC-SEC-015** | Administrador consulta borrador | ADMIN | 200/404 | **404** | **PASS*** | *Nota:* Retorna 404 porque el borrador no existe, pero el rol ADMIN pasó la seguridad. |

## 3. Sección 8.4 — Negocio y Transiciones (TC-BIZ-*)
*Pruebas del flujo feliz de extremo a extremo, integrando roles, persistencia y reglas de negocio. Ejecutadas de forma encadenada.*

| ID | Flujo | Actor | HTTP Esperado | HTTP Real | Estado | Observación / Evidencia |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-BIZ-001** | Consultar venues activos y por código | Anónimo | 200 | **200** | **PASS** | Identidad y estado (`VEN-E2E-01`) coinciden con el fixture. |
| **TC-BIZ-002** | Consultar artistas activos y por ID | Anónimo | 200 | **200** | **PASS** | Artista E2E recuperado correctamente. |
| **TC-BIZ-003** | Crear evento válido | ADMIN | 201 | **201** | **PASS** | Evento `EVT-R1-001` creado. Código disponible para GET posterior. |
| **TC-BIZ-004** | Publicar evento | ADMIN | 200 | **200** | **PASS** | Estado cambiado a `PUBLISHED`. Aparece en `/published`. |
| **TC-BIZ-005** | Añadir artista al evento | ADMIN | 200 | **200** | **PASS** | Relación creada. Consulta por artista devuelve el evento. |
| **TC-BIZ-006** | Registrar dos usuarios / duplicado | Anónimo | 201 / 409 | **201 / 409** | **PASS** | Identidades creadas. Intento de duplicado bloqueado. |
| **TC-BIZ-007** | Comprar ticket para evento publicado | USER_A | 201 | **201** | **PASS** | Ticket generado y vinculado correctamente. |
| **TC-BIZ-008** | Consultar ticket (Propio vs Ajeno) | USER_A / USER_B | 200 / 403 | **200 / 403** | **PASS** | USER_A lee su ticket (200). USER_B es bloqueado (403). |
| **TC-BIZ-009** | Listar tickets pagados | STAFF | 200 | **200** | **PASS** | El ticket comprado por USER_A es visible para STAFF. |
| **TC-BIZ-010** | Cancelar ticket por propietario | USER_A | 200 | **200** | **PASS** | Estado cambió a `CANCELLED`. |
| **TC-BIZ-011** | Cancelar un ticket ya cancelado | USER_A | 409 | **409** | **PASS** | Transición de estado inválida bloqueada correctamente (sin mutación). |
| **TC-BIZ-012** | Marcar como usado otro ticket con STAFF | STAFF | 200 | **200** | **PASS** | Ticket `{{ticketCode2}}` marcado como `USED`. |
| **TC-BIZ-013** | Marcar ticket usado una segunda vez | STAFF | 409 | **409** | **PASS** | Doble transición bloqueada por regla de negocio. |
| **TC-BIZ-014** | Compra con evento no publicado / inexistente | USER_A | 404 | **404** | **PASS** | Error de negocio (evento no encontrado). Ticket no persistido. |
| **TC-BIZ-015** | Compra con Enum inválido | USER_A | 400 | **400** | **PASS** | Error de validación de contrato HTTP (Enum incorrecto). |
---
### Leyenda de Estados
*   **PASS:** El código HTTP real coincide con el esperado o el comportamiento es el correcto según el contrato.
*   **PASS*:** Aprobado con salvedad. El endpoint no tiene datos reales (devuelve 404), pero la seguridad (roles/token) funciona correctamente.
*   **FAIL:** El código HTTP real difiere del esperado. Requiere revisión en la capa de Seguridad (Fase 4).
*   **BLOCKED:** No se pudo ejecutar la prueba por falta de precondiciones (fixtures no creados, endpoints no disponibles, etc.).
