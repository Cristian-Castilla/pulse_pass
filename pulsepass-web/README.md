# PulsePass Web

Frontend académico de PulsePass (Fase 5). Consume la API con la ampliación autorizada de perfil y titular; conserva las rutas HTTP, permisos y migraciones existentes.

## Ejecutar

Requiere Node >= 22.22.0. Verificado en esta máquina con Node 26.7.0 y npm 11.19.0.

```bash
cd pulsepass-web
npm ci --include=dev
npm run dev
```

Abrir la URL que indique Vite (por defecto http://127.0.0.1:5173). La API debe estar disponible en http://localhost:8080 para usar datos reales. El proxy de desarrollo reenvía `/api`; no se inicia Spring Boot ni PostgreSQL desde este proyecto.

```bash
npm run lint
npm run build
npm test -- --run
npm run format:check
```

`npm test` es finito; el lanzador fija NODE_ENV=test incluso si el shell heredó production. También acepta filtros, `--run`, `--reporter=json` y `--outputFile=...`. `dist/` contiene la compilación; el servidor que la publique debe resolver rutas SPA a `index.html` y servir/proxificar `/api`. No se ha realizado despliegue productivo.

## Variable pública

`.env.example` documenta `VITE_API_BASE_URL=/api`. No hace falta copiarla para el proxy por defecto. Un archivo `.env.local` puede cambiar esa URL en un entorno controlado. **Nunca** introducir una clave JWT, credenciales de base de datos, contraseñas ni secretos en variables `VITE_*`: pasan al navegador. Las fuentes Inter y Poppins se sirven desde el propio bundle, no desde servicios externos.

## Sesión y roles

Registro -> login -> `/api/auth/me`. La identidad y los permisos proceden del servidor, no de decodificar el JWT. Los roles `ROLE_USER`, `ROLE_STAFF`, `ROLE_ADMIN` se validan y transforman a sus equivalentes de UI.

- USER: compra y tickets propios.
- STAFF: validación de ingreso y tickets pagados.
- ADMIN: operación STAFF y administración de eventos.
- Cuenta con varios roles: los enlaces se habilitan por pertenencia; la compra permanece oculta a STAFF/ADMIN conforme al PRD frontend.

El token solo vive en memoria/sessionStorage y viaja en Authorization. Un 401 autenticado, el vencimiento o salir eliminan la identidad y la caché privada. Se cancelan consultas y se descartan respuestas de una sesión anterior, también si llegan después de cambiar de usuario. No hay refresh token ni logout en servidor. sessionStorage sigue siendo accesible a XSS; es la limitación aceptada del PRD.

No se incluyen credenciales de demostración ni se provisionan usuarios. Para una demostración real, el responsable del backend debe preparar USER, STAFF y ADMIN siguiendo `../docs/operacion-segura.md` en una base autorizada. MSW se utiliza solo en pruebas: **la aplicación no muestra datos ficticios cuando falta la API**.

## Rutas

Públicas: `/`, `/eventos`, `/eventos/:eventCode`, `/artistas`, `/artistas/:id`, `/sedes`, `/sedes/:code`, `/login`, `/registro`, `/403` y la página comodín 404.

Autenticada: `/cuenta`.

USER: `/mis-tickets`, `/mis-tickets/:ticketCode`, `/compra/confirmacion/:ticketCode`.

STAFF/ADMIN: `/staff/validacion`, `/staff/eventos/:eventCode/tickets`. El enlace de navegación abre un formulario para introducir el código del evento.

ADMIN: `/admin/eventos`, `/admin/eventos/nuevo`, `/admin/eventos/:eventCode`.

Las 20 entradas del mapa del PRD están registradas. Inicio y Layout son inmediatos; las demás páginas se cargan de forma perezosa. Los errores de ruta tienen un componente separado.

## Contratos, límites y bloqueos aprobados

El PRD enumera 20 operaciones y 3 referencias de autenticación, pero POST /api/users aparece dos veces: hay **22 operaciones únicas**, no 23 endpoints distintos. Se implementaron y probaron los 22 adaptadores HTTP. Véase [contratos y trazabilidad](docs/contratos.md) y la colección Postman de referencia en `docs/PulsePass.postman_collection.json` (no ejecutada contra tu base).

El usuario autorizó ampliar los datos exigidos por los diseños. No se reemplazan nombres con correos ni se fabrican perfiles:

- Cuenta: UserResponse añade firstName, lastName y birthDate. La UI consulta únicamente la identidad propia; el servidor conserva la autorización de propietario o ADMIN. `/api/auth/me` mantiene su respuesta mínima sin datos de perfil.
- Detalle de ticket y validación STAFF: TicketResponse añade userFullName, calculado desde UserProfile, sin reemplazar userEmail ni incluir nacimiento, teléfono o ciudad.
- Tickets pagados: tabla completa, tarjetas móviles y acción Usar con invalidación de caché y manejo de errores. TEST-FE-STF-004 está ejecutado y aprobado.
- Los perfiles legados pueden estar ausentes o no tener nacimiento: esos campos se devuelven como null y se muestran como no informados. No se rellenan datos persistentes.
- **Compatibilidad:** iniciar/publicar el backend ampliado antes del frontend. Los esquemas exigen las nuevas claves, aunque admiten null; una API antigua produce un error de contrato, no un perfil ficticio.
- **Catálogo resuelto sin tocar backend:** cada tarjeta obtiene el detalle por eventCode y su sede por venueCode, reutilizando TanStack Query. Así muestra la ciudad real, aunque EventSummaryResponse no la incluya. El coste es una consulta de detalle por evento y una consulta por sede no cacheada; no se inventa un listado adicional ni se cargan todos los eventos en ADMIN.
- ADMIN recuerda únicamente códigos de los eventos creados en la pestaña; los datos del evento se mantienen en la caché, no se duplican en Context. No existe listado global en la API.

Los bloqueos de contrato de perfil/titular están resueltos en código y pruebas locales. El DoD completo **no está cerrado**: siguen pendientes la integración real, la suite completa de PostgreSQL/Testcontainers, rendimiento móvil y navegadores externos a este entorno.

## Diseño y calidad

Se recuperaron las 32 imágenes rasterizadas que ya estaban incorporadas en el PDF, con los nombres de su índice, dentro de [docs/diseno](docs/diseno/README.md). No se modificó el PRD. Son referencias, no capturas de una implementación supuestamente terminada.

Sistema visual: Poppins/Inter, indigo/rosa, Tailwind v4, tema claro/oscuro, SVG de trazo, foco visible, controles táctiles de al menos 44px, diálogos nativos y respeto por movimiento reducido. Los importes se muestran en COP conservando hasta dos decimales; no se calculan precios ni reglas de edad/capacidad en el cliente.

Las pruebas usan Vitest, Testing Library, user-event y MSW; no necesitan Spring, Docker ni red real. Las fechas de las pruebas están fijadas. La verificación visual temporal y axe se ejecutan contra fixtures claramente identificados; no sustituyen la integración con PostgreSQL o la API real. No se añadió una suite E2E ni Playwright como dependencia del producto.

Para la evidencia y los límites de ejecución, consultar [reporte final](docs/reporte-verificacion.md).
