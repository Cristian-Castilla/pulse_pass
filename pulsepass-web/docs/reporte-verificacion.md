# Reporte de implementación y verificación — Frontend PulsePass

## Actualización posterior — configuración local y sedes demo

- Creada `V6__insert_initial_venues.sql` con las siete sedes solicitadas, activas, aforo ficticio 1000 y dirección pendiente. No se modificaron V1–V5.
- SQL comprobado en una tabla temporal de PostgreSQL: inserción inicial, repetición sin duplicados y conservación de una sede previamente modificada. La transacción de prueba se revirtió.
- Arranque real de Spring contra PostgreSQL: seis migraciones validadas; V6 aplicada por Flyway. Una consulta JDBC posterior confirmó `version=6`, `success=true` y las siete sedes demo activas.
- La configuración privada `.env.properties` se creó fuera de Git, con una clave JWT criptográfica nueva y acceso restringido al usuario local y SYSTEM. No se incluye en las evidencias ni en los commits.
- API real: `GET /api/venues/active` devolvió 200 con las siete sedes. Un registro inválido `{}` desde `http://localhost:5173` devolvió 400 con el origen CORS autorizado, sin crear una cuenta.
- Maven compiló los cambios y ejecutó `ApplicationConfigurationTest`: una prueba aprobada. La prueba con Testcontainers agotó su espera; no se presenta como aprobada ni como suite completa.
- Se detuvo el proceso de verificación propio. No se detuvieron otros procesos del usuario. Sin staging, commit ni push.

Esta comprobación real es focalizada en arranque, sedes y CORS: no cierra la aceptación integral de login, compras, administración ni el gate de rendimiento móvil. Las evidencias anteriores conservan su alcance original.

## Estado actual — ampliación autorizada de perfil y titular

El usuario autorizó entregar los datos exigidos por el PRD. Los bloqueos de contrato están resueltos en código: Cuenta muestra nombres, apellidos y nacimiento; detalle de ticket y validación STAFF muestran el titular; la tabla de pagados incluye titular, precio, estado y acción Usar, con variante móvil e invalidación de caché.

- `UserResponse`: añade firstName, lastName y birthDate. Se mantiene propietario o ADMIN autorizado. El registro enlaza ambos lados de User/UserProfile antes del mapeo.
- `TicketResponse`: añade únicamente userFullName, sin reemplazar userEmail ni exponer nacimiento, teléfono o ciudad.
- `/api/auth/me`, las rutas y los permisos no cambian. La sesión frontend no requiere ni conserva los campos de perfil en su identidad.
- Registros legados: perfil ausente o nacimiento nulo se devuelven/muestran como no informados; no se inventan ni escriben datos.
- Sin cambios en entidades, PRD, Flyway, dependencias, credenciales o base persistente. Sin staging, commit ni push.

### Evidencia de esta ampliación

| Verificación                                          | Resultado                                                            |
| ----------------------------------------------------- | -------------------------------------------------------------------- |
| Backend: compilación principal y de todas las pruebas | Correcta mediante Maven                                              |
| Backend: selección de 9 clases, sin Docker            | 108 pruebas, 0 fallos, 0 errores, 0 omitidas                         |
| Frontend: ESLint y build de producción                | Correctos                                                            |
| Frontend: suite completa Vitest                       | 101 aprobadas, 0 fallidas, 0 pendientes; 20 archivos                 |
| Identificadores mínimos del PRD                       | 50 identificadores con pruebas aprobadas, incluido TEST-FE-STF-004   |
| QA focalizada del build con API simulada              | 24/24: 4 vistas × 3 anchos (360/390/1280) × 2 temas                  |
| axe en esas 24 vistas                                 | 0 violaciones detectadas; sin desbordamientos ni errores JS          |
| Acción Usar en navegador simulado                     | Verificada en tabla y validación, con lectura del estado actualizado |

Se verificó RED→GREEN para los nuevos mappers, perfil visible, nombre del titular y contrato/tabla. Dos aserciones nuevas necesitaron corrección: normalización del espacio no separable de COP en Testing Library y mensaje genérico de 403. No se alteró el cliente HTTP ni el formato monetario para hacerlas pasar.

Evidencias nuevas: `docs/evidencias/perfil-tests-final.json`, `perfil-backend-resumen.json` y `perfil-visual/`. Las verificaciones del navegador usan fixtures declarados, **no prueban integración con Spring/PostgreSQL**. Los resultados Lighthouse de abajo son históricos; no se repitieron en esta ampliación.

**El DoD total sigue abierto:** falta la suite completa de PostgreSQL/Testcontainers, la integración real, la aceptación de rendimiento móvil y la comprobación de navegadores fuera de este entorno. El backend ampliado debe publicarse antes del frontend: la API antigua produce un error de contrato. STAFF conserva el alcance global del MVP; debe limitarse por evento antes de producción si el modelo lo requiere.

### Gate que ejecuta el usuario

Con Docker disponible, desde la raíz del repositorio (Git Bash):

```bash
"C:/Program Files/Java/jdk-21.0.11/bin/java" -Xmx256m -classpath "C:/Users/57321/apache-maven-3.9.16/boot/plexus-classworlds-2.11.0.jar" -Dclassworlds.conf="C:/Users/57321/apache-maven-3.9.16/bin/m2.conf" -Dmaven.home="C:/Users/57321/apache-maven-3.9.16" -Dmaven.multiModuleProjectDirectory="C:/Users/57321/Desktop/pulse_pass" org.codehaus.plexus.classworlds.launcher.Launcher clean test '-DargLine=-Xmx384m -XX:ActiveProcessorCount=2'
```

La ejecución focalizada no sustituye ese gate; no se dejó una suite backend pesada ejecutándose en segundo plano.

---

## Registro histórico — entrega anterior a la ampliación

El resto del documento preserva la evidencia y restricciones de la entrega inicial. Sus bloqueos de perfil/titular y la prohibición de tocar backend fueron reemplazados por la autorización y los resultados anteriores; no describen el estado actual.

## Veredicto

Implementación ejecutada en `pulsepass-web/`, rama `feature/pulsepass-frontend`, sin commits ni push. El código compila y las comprobaciones locales pasan, pero **el DoD completo sigue abierto**: faltan datos de perfil/titular en la API, el rendimiento móvil medido no alcanza el objetivo y no se hizo integración con la API real.

No se modificaron backend, migraciones, PRD, README raíz ni archivos previos del usuario. El único directorio nuevo del proyecto es `pulsepass-web/`. Las herramientas y sondas temporales de QA están fuera del repositorio.

## Decisiones del usuario aplicadas

- Ejecutar las 11 etapas sin paradas intermedias; no hacer commit ni push.
- Mantener intactos el backend y las reglas de negocio.
- Exigir datos completos de los diseños: bloquear las partes que realmente requieren ampliar la API, no inventar valores ni reemplazar nombres por correos.
- Usar las imágenes del PDF; los 32 originales externos no estaban disponibles. Se recuperaron los 32 objetos raster incorporados en el documento y se preservaron en `docs/diseno/`.
- Pruebas MSW y verificación visual local; no arrancar Spring contra la base persistente ni escribir en ella.

## Estado de las etapas

| Etapa               | Resultado                                    | Evidencia o pendiente                                                                       |
| ------------------- | -------------------------------------------- | ------------------------------------------------------------------------------------------- |
| 1. Contratos        | Completa por lectura y adaptadores simulados | Esquemas por funcionalidad, `docs/contratos.md`, colección Postman de 22 operaciones        |
| 2. Proyecto base    | Completa                                     | Vite/React/TS estricto/Tailwind; build y lint correctos                                     |
| 3. UI genérica      | Completa                                     | Controles, diálogo nativo, teclado, errores, avisos de 4 s y pruebas                        |
| 4. Router/layouts   | Completa                                     | 20 entradas de ruta, lazy, navbar por roles, 403/404 y errorElement                         |
| 5. HTTP             | Completa localmente                          | Bearer, zod, ApiError, MSW, reintentos y respuestas tardías                                 |
| 6. Catálogo público | Completa localmente                          | Inicio, filtros URL, tarjetas, detalles, artistas y sedes                                   |
| 7. Autenticación    | Parcial por contrato                         | Login/registro/guardas/sesión probados; perfil completo de Cuenta bloqueado                 |
| 8. Tickets USER     | Parcial por contrato                         | Compra/confirmación/lista/cancelación probadas; nombre de titular en detalle bloqueado      |
| 9. STAFF            | Parcial por contrato                         | Búsqueda/uso probados; validación visual del titular y tabla de pagados bloqueadas          |
| 10. ADMIN           | Completa localmente                          | Buscar/crear/publicar/asociar; códigos de sesión sin duplicar datos de servidor             |
| 11. Calidad         | Verificada con pendientes                    | Build/lint/tests/axe correctos; rendimiento móvil insuficiente e integración real pendiente |

La ciudad de las tarjetas **no quedó bloqueada**: se obtiene por los endpoints existentes de detalle de evento y sede, con consultas cacheadas. Esto añade peticiones por evento/sede no cacheada, pero no modifica backend ni inventa datos.

## Ejecución real de herramientas

Última consolidación: 2026-11-19T02:02:02 (hora local de esta máquina).

| Comprobación               | Resultado                                                                             |
| -------------------------- | ------------------------------------------------------------------------------------- |
| `npm run lint`             | Exit 0, sin errores ni warnings de ESLint                                             |
| `npm run build`            | Exit 0; TypeScript estricto y bundle de producción generados                          |
| `npm test -- --run`        | 93 aprobadas, 0 fallidas, 1 TODO explícito; 94 casos declarados                       |
| Descubrimiento de pruebas  | 19 archivos de pruebas presentes en el reporte final                                  |
| Tests mínimos del PRD      | 49 de 50 identificadores tienen evidencia aprobada; STF-004 sigue pendiente           |
| `npm audit --json`         | 0 vulnerabilidades reportadas en este árbol npm; no es una certificación de seguridad |
| Referencias visuales       | 32 PNG recuperados del PDF, sin modificar el documento                                |
| Recorrido visual           | 120 casos únicos: 20 rutas × 3 anchos × 2 temas                                       |
| Responsive                 | 360, 390 y 1280 px; 0 desbordamientos horizontales en esos casos                      |
| Errores JS en el recorrido | 0                                                                                     |
| axe WCAG 2/2.1 A/AA        | 40 ejecuciones de escritorio (20 rutas × 2 temas); 0 violaciones detectadas           |
| Bundle                     | No contiene los tokens ficticios de los fixtures de pruebas                           |

Las capturas y los resultados visuales usan respuestas de prueba declaradas, no respuestas del backend real. Se conservaron ocho capturas representativas y el resumen completo de los 120 casos en `docs/evidencias/`. El recorrido visual es una inspección de calidad, no una suite E2E de flujos de negocio.

**Único TODO de Vitest:** `TEST-FE-STF-004`, tabla con titulares. No se reemplazó por una aserción vacía ni se presentó como aprobada. Las secciones bloqueadas de Cuenta, DetalleTicket y ValidacionIngreso también mantienen abierto el DoD aunque los comportamientos disponibles estén cubiertos.

## Lighthouse sobre el bundle de producción

Se sirvió `dist/` con un servidor **temporal** local de QA y fixtures identificados, sin base de datos y sin llamar a Spring. No se añadió dicho servidor al producto. Se conservan los JSON completos de las mediciones finales.

| Perfil estándar                                           | Performance | Accessibility | Resultado                    |
| --------------------------------------------------------- | ----------- | ------------- | ---------------------------- |
| Escritorio (`--preset=desktop`)                           | 92/100      | 100/100       | Supera el objetivo de 80     |
| Móvil (perfil por defecto, sin cambiar multiplicador CPU) | 69/100      | 100/100       | No alcanza el objetivo de 80 |

Lighthouse advirtió que la CPU de prueba es más lenta de lo esperado. Eso es una limitación de la medición, **no prueba que el frontend esté libre de problemas de rendimiento**. No se cambió el throttling para fabricar un resultado verde y no se ocultó la ejecución móvil. El gate de rendimiento móvil queda abierto; repetir en hardware calibrado y perfilar el hilo principal antes de decidir una optimización.

## Bloqueos precisos

1. **Cuenta:** `UserResponse` no devuelve nombres, apellidos ni nacimiento. No se guardó el formulario de registro como sustituto del perfil del servidor.
2. **Titular de ticket:** `TicketResponse` devuelve userEmail pero no el nombre completo. No se usó el correo como sustituto ni se consultaron datos de otra cuenta mediante STAFF.
3. **Tabla de pagados:** requiere nombres por fila; se mantiene bloqueada, no se entregó una tabla simplificada como si cumpliera el diseño. Esto deja pendiente también su variante móvil.
4. **Integración real:** no se verificaron login/JWT reales, usuarios de demostración, CORS efectivo o reglas contra PostgreSQL desde el navegador.
5. **Rendimiento móvil:** 69/100 en la última medición estándar local, por debajo del objetivo.

Resolver 1–3 requiere autorización de una fase/backend diferente y después actualizar esquemas, vistas y pruebas. No se hizo ninguna ampliación de API por cuenta propia.

## Límites y advertencias

- La verificación visual usa Microsoft Edge instalado en esta máquina. No se certificaron las últimas dos versiones de Chrome, Edge, Firefox y Safari ni lectores de pantalla reales.
- El test runner con Node 26 emite una advertencia experimental sobre su localStorage nativo. La aplicación no usa localStorage para el token; las pruebas usan sessionStorage de jsdom. La advertencia no hizo fallar la suite.
- npm indicó que la línea ESLint 9 instalada ya no tiene soporte; lint funciona y audit no reporta advisories. La migración a otra versión mayor no se mezcló con el alcance funcional.
- Las guías externas de React/Router/Tailwind mencionadas por el PRD no estaban suministradas; no se afirma haberlas leído.
- El almacenamiento de JWT en sessionStorage sigue expuesto a XSS, según el riesgo aceptado por el PRD. No hay refresh ni revocación de tokens en este frontend.
- No hay pagos, QR, CRUD no solicitado, SSR, PWA, despliegue, CI/CD ni suite E2E añadidos.
- No se ejecutó Maven ni la suite backend: no hubo cambios Java y no se requirió Docker.

## Integridad y reproducción

SHA-256 consolidado de fuentes frontend/configuración/package-lock verificado tras la suite: `144f760a976835c8a40d95c3410e895e6e6c859c8e4374da88278c235f5a4a4f`.

El conjunto incluye todos los archivos de `src/`, package.json, package-lock.json, vite.config.ts, tsconfig.json y eslint.config.js, ordenados por ruta; se hashea concatenando ruta relativa y bytes de cada archivo. Los informes/docs no forman parte del código ejecutado.

```bash
cd pulsepass-web
npm ci --include=dev
npm run lint
npm run build
npm test -- --run
npm run dev
```

Para integración real, preparar antes una API y cuentas de demostración en una base autorizada. La aplicación no ofrece modo de demostración ficticio en producción. No se puede cerrar el DoD ni proponer esta fase como completamente aceptada hasta resolver los pendientes anteriores.
