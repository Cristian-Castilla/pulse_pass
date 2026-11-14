# Auditoría completa de PulsePass — PRD y guías del profesor

> Diagnóstico histórico anterior a las correcciones. El estado de implementación y la evidencia
> posterior se mantienen en `Correcciones_Auditoria_PulsePass.md`; los resultados de abajo no
> certifican por sí solos la versión corregida.

## 1. Dictamen

**POR CORREGIR. No se puede declarar completa la Definition of Done de las cuatro fases.**

El proyecto compila y la suite vigente pasa: **264 pruebas en 27 suites, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS**. Sin embargo, hay defectos comprobados fuera de esa cobertura: configuración de persistencia mal ubicada, autorización STAFF más amplia que el PRD, compra sin exigir USER y validación JWT que no impone expiración ni HS256.

**COMPLETO:** estructura principal de las cuatro capas, modelo relacional, consultas, interfaces/DTO/MapStruct, reglas comerciales principales y base de seguridad. **POR CORREGIR:** permisos, validación de tokens, configuración efectiva, manejo de ciertos errores HTTP y pruebas de aceptación incompletas. **CRÍTICO para aprobar/usar:** el arranque con la configuración documentada queda bloqueado salvo overrides externos correctos; permisos indebidos y tokens fuera de contrato deben corregirse antes de exposición a terceros. No se demostró acceso anónimo con firma falsa ni pérdida de datos reales.

Esta fue una auditoría de diagnóstico: **no se corrigió código, no se modificaron PRD ni guías, no se hizo commit/push y no se conectó a la base persistente del usuario**. Los únicos cambios del repositorio son este informe nuevo y los artefactos de build ignorados. Los documentos no rastreados que ya existían se conservaron.

## 2. Base y método

- Repositorio: `C:/Users/57321/Desktop/pulse_pass`.
- Rama: `feature/rebuild-pulsepass`.
- HEAD: `5c9056f385f0f459f39b809029718db2b8f0ee98`.
- Sin conflictos pendientes ni cambios rastreados al inicio. Al finalizar, los hashes de los archivos rastreados inspeccionados permanecen iguales (excluidos archivos `.env*`, que no se leyeron).
- Stack comprobado: Spring Boot 4.1.1, Java 21, Maven, PostgreSQL/Testcontainers, Flyway, MapStruct, Spring Security y JJWT 0.13.0.
- Se leyeron los cuatro PRD completos y las tres guías completas. El contrato específico de PulsePass prevalece sobre ejemplos educativos de CourseHub/University API.
- Revisiones paralelas de persistencia, servicios y seguridad; revisión central de controladores, configuración, documentación y ejecución.
- Se separan **implementado por lectura**, **escenario probado realmente**, **ejecución actual**, **brecha de cobertura**, **desviación estructural** y **mejora opcional**. No se asigna porcentaje arbitrario de avance.

### Fuentes autoritativas

| Fuente | Alcance |
|---|---|
| `PRD_PulsePass.md` | Persistencia, consultas, integridad, Flyway y PostgreSQL |
| `PRD_PulsePass_Capa_Servicios.pdf` | Contratos, reglas comerciales, DTO/MapStruct, transacciones y unitarios |
| `PRD_PulsePass_Capa_Controladores_v2.pdf` | 20 operaciones, validación, HTTP, errores y pruebas contractuales |
| `PRD_PulsePass_Capa_Seguridad_Spring_Security_JJWT.pdf` | Login, registro, identidad, roles/ownership, JWT, NFR y regresión |
| `guia/Spring Framework, Spring Boot y arquitectura del Backend.md` | IoC/DI, configuración externa/tipada, arquitectura y separación de responsabilidades |
| `guia/capa service.md` | Interfaces, records, MapStruct, Optional, negocio, transacciones y Mockito |
| `guia/Guia_Didactica_Spring_Security_JJWT_Mermaid.pdf` | Flujo de autenticación, BCrypt, JJWT, filtro, contexto y stateless |

Los planes de acción, estado de sesión, matriz y README son fuentes secundarias: no se usaron para sustituir requisitos.

## 3. Verificación ejecutada

### Suite nueva, no resultados históricos

Se preservaron los reportes previos antes de `clean`. Después se ejecutó el equivalente real a `mvn clean test` mediante el launcher de Maven por classpath, necesario en este Windows/MSYS.

```text
Compilación main: 77 archivos fuente
Compilación test: 30 archivos fuente
Tests run: 264, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 17:55 min
Exit code: 0
```

Se contrastaron programáticamente las **264 anotaciones @Test** actuales con los XML Surefire: **todas las 27 clases actuales de prueba se ejecutaron**. No hay clases actuales excluidas. Se creó un contenedor PostgreSQL de pruebas; la descarga inicial de la imagen fue lenta y requirió un reintento, pero no impidió terminar.

| Grupo de suites | Alcance |
|---|---|
| Persistencia y credenciales | Repositories, relaciones, Flyway y credenciales/roles |
| Servicios originales | Venue, Artist, Event, User y Ticket con Mockito |
| Autenticación/autorización | Unitarios Auth/Authorization, JwtService y pruebas integradas de identidad/login |
| HTTP | Cinco controladores con servicios reales, MockMvc y filtros activos |
| Seguridad | Matriz, ownership, visibilidad y login HTTP |

**Importante:** los reportes históricos sumaban 269 porque incluían cinco pruebas de `FlywayMigrationOnExistingSchemaTest`, cuya fuente **no existe en este HEAD**. Después de `clean`, el total real es 264. No se atribuye ese test de otra rama a esta ejecución.

### Probes adicionales ejecutados fuera del repositorio

1. **Configuración:** `YamlPropertySourceLoader` real confirmó que existen `pulsepass.datasource.url` y `pulsepass.jpa.hibernate.ddl-auto`, pero no sus equivalentes `spring.*`. `DataSourceProperties` inicializado sin URL produjo `DataSourceBeanCreationException`; no se inició una conexión ni se cargó `.env`.
2. **HTTP:** MockMvc con ArtistController y GlobalExceptionHandler reales devolvió **500** al omitir `stageName`; el Service mock no fue invocado. Un ID no numérico devolvió 400.
3. **Control negativo descartado:** CreateEventRequest con minimumAge ausente/null devolvió 400 sin llamar al Service; con minimumAge=0 devolvió 201 y sí lo invocó. **No se reporta un bypass de minimumAge por usar int:** Jackson 3 fue estricto en la ejecución comprobada.
4. **Autorización:** AuthorizationService y servicios reales, con repos/mappers simulados y objetos exclusivamente ficticios: STAFF ajeno obtuvo usuario por email y username, listó tickets ajenos y canceló un ticket cuyo estado pasó a CANCELLED. STAFF propietario sin USER también ejecutó compra.
5. **JWT real:** clave aleatoria efímera de prueba, sin imprimir clave ni token. El validador aceptó JWT firmado sin exp, HS384 con la misma clave válida, sin iat/jti y con iat futuro. Se verificó el sujeto retornado mediante assertions.
6. **Filtro:** el análisis de seguridad ejecutó el filtro real con requests simulados: dos Authorization separados se interpretaron según el primero; válido primero autentica, inválido primero no. No hay falsificación sin un token válido.

Todos esos probes finales tuvieron exit 0, que aquí significa **reproducción satisfactoria del comportamiento auditado**, no que el defecto se haya arreglado. Los probes de servicio no se presentan como explotación HTTP/BD real.

## 4. COMPLETO — qué está presente y qué ejecución lo respalda

| Capa | Implementación comprobada | Alcance de la evidencia actual |
|---|---|---|
| Persistencia | Entidades/relaciones del MVP, ocho repositories, constraints, enums STRING, BigDecimal/DECIMAL y V1–V4 acumuladas | Tests actuales verdes sobre PostgreSQL; varias aserciones de integridad/upgrade siguen incompletas |
| Servicios | Cinco interfaces/implementaciones, constructor injection, DTO record, MapStruct, excepciones, Optional seguro y transacciones | Unitarios originales ejecutados; no todas las reglas/ramas ni la política de precios están afirmadas |
| Negocio | DRAFT inicial; publicación; artista no duplicado; usuario activo; compra PUBLISHED/futura; edad a fecha del evento; capacidad PAID; SOLD_OUT; cancelación/uso | Lectura y casos existentes verdes; se detallan huecos sin calificarlos automáticamente como bugs |
| Controladores | 20 rutas heredadas conservadas, DTO/ResponseEntity, 201 para creación y 200 para consultas/actualizaciones, validación y advice central | Pruebas HTTP con filtros activos verdes; pérdida de aislamiento y algunos casos de error sin evidencia |
| Seguridad base | BCrypt, registro HTTP transaccional, login username/email, subject por ID, roles/estado vigentes desde BD, stateless y ocultamiento de drafts | Pruebas de login/ownership/visibilidad verdes; permisos STAFF y validación JWT incompletos |
| Secretos/config JWT | Sin clave productiva por defecto; `.env*` ignorado y `.env.example` exceptuado | Ignore comprobado; no se abrieron secretos reales ni se certifica todo el historial |

**Trazabilidad HTTP:** 20 operaciones heredadas y dos nuevas de autenticación. Distribución por clase: Venue 2, Event 7, Artist 3, User 3, Ticket 5 y Auth 2. La consulta de tickets pagados por evento vive correctamente en EventController como subrecurso.

## 5. POR CORREGIR — registro consolidado de hallazgos

Se agrupan causas repetidas entre capas; los anexos desarrollan requisitos, evidencia y matrices completas. **P1**: atender primero por bloqueo funcional o seguridad. **P2**: cumplimiento obligatorio/evidencia/arquitectura. **P3**: exactitud o mantenibilidad de menor impacto. No se declara una incidencia P0 de explotación anónima o destrucción de datos.

### A01 — P1 / bloqueo de la configuración documentada: persistencia bajo namespace incorrecto

- **Contrato:** Persistencia §13, QT-002 y DoD; guía arquitectura §§41–52.
- **Dónde:** `src/main/resources/application.yml:21-48`, especialmente :28, :33 y :46.
- **Actual:** datasource, jpa y flyway son hijos de `pulsepass`, no de `spring`. DB_URL/DB_USER/DB_PASSWORD no alimentan la autoconfiguración por esos bloques; validate y open-in-view=false no se aplican. Flyway puede funcionar por defaults: no se afirma que deje de migrar.
- **Impacto:** sin propiedades Spring correctas aportadas por otra fuente, la configuración de arranque documentada no proporciona datasource. La suite con ServiceConnection oculta esa falta. El texto validate no acredita validación efectiva de esquema.
- **Prueba:** namespaces comprobados con loader real; DataSourceProperties sin URL falla. No se arrancó contra BD personal ni se presume el contenido de overrides externos.
- **Mínimo:** volver a colocar esos tres bloques bajo spring y comprobar propiedades efectivas/arranque contra PostgreSQL aislado. Revisar el YAML homónimo de tests, que solo fija JWT, para que no oculte la configuración que se desea probar.

### A02 — P1 / autorización: STAFF accede y muta donde solo propietario/ADMIN deben pasar

- **Contrato:** Seguridad §11 USR-02/03, TKT-03 y TKT-05; §11.1.
- **Raíz:** `src/main/java/com/pulsepass/pass/service/impl/AuthorizationServiceImpl.java:58-74` permite STAFF para cualquier owner.
- **Alcance:** `UserServiceImpl.java:81,91`; `TicketServiceImpl.java:164,191`; las cuatro rutas caen en `SecurityConfig.java:131` authenticated.
- **Impacto demostrado:** STAFF ajeno lee ambas búsquedas de usuario, lista tickets y cancela un PAID futuro. Se comprobó con servicios reales y mocks persistentes, no con datos reales.
- **Mínimo:** distinguir dueño-o-ADMIN de dueño-o-STAFF/ADMIN. Conservar STAFF para TKT-02 y uso, donde sí corresponde. Probar las cuatro denegaciones con recurso existente y ausencia de mutación. Cancelación propia debe respetar USER/ADMIN según matriz.

### A03 — P1 / autorización: compra no exige ROLE_USER

- **Contrato:** Seguridad §11 TKT-01.
- **Dónde:** `SecurityConfig.java:110-131`; `AuthorizationServiceImpl.java:89-99`; `TicketServiceImpl.java:58-68`.
- **Actual:** requireSelf comprueba ID/activo, pero no rol; fallback solo authenticated. STAFF sin USER compró para sí en el probe. ADMIN-only y cuenta sin roles siguen el mismo camino por lectura; no se simularon como ejecuciones separadas.
- **Impacto:** retirar USER no impide comprar; no existe jerarquía de roles implícita declarada.
- **Mínimo:** exigir USER en compra y conservar comparación de ID, incluyendo frontera de servicio si se protege también acceso directo. Casos USER propio/ajeno, STAFF-only, ADMIN-only, sin roles y múltiples roles.

### A04 — P1 / JWT: no se imponen exp ni algoritmo HS256 al recibir

- **Contrato:** Seguridad FR-SEC-AUTH-006; §§12.1,12.2,14.4 y DoD.
- **Dónde:** `src/main/java/com/pulsepass/pass/security/JwtServiceImpl.java:121-148`; :62 y :189-190 son constante/getter, no enforcement.
- **Actual demostrado:** acepta token correctamente firmado **sin exp** y token **HS384**, aunque la emisión propia sí añade exp y usa HS256.
- **Impacto:** acepta credenciales fuera del contrato, incluida una sin caducidad. **La firma se verifica: no permite a alguien sin la clave falsificar un JWT.**
- **Mínimo:** exigir exp presente/futura y algoritmo recibido HS256; mantener verificación de firma/issuer/sub. Tests negativos con firmas válidas y clave suficientemente larga para no rechazar por otra causa.

### A05 — P2 / JWT: iat y jti no obligatorios en validación

- **Contrato:** Seguridad §12.1.
- **Dónde:** `JwtServiceImpl.java:127-139`.
- **Actual demostrado:** acepta falta de iat/jti e iat futuro; no se definió/aplicó política temporal.
- **Mínimo:** exigir metadatos, coherencia temporal y tolerancia documentada; probar cada omisión. No introducir denylist/refresh tokens: fuera de alcance.

### A06 — P2 / HTTP: falta de query param requerido termina como 500

- **Contrato:** Controladores §§11.4,13–16 y NFR-CTRL-003.
- **Dónde:** `ArtistController.java:30-31`; `GlobalExceptionHandler.java:131-140`.
- **Actual demostrado:** GET /api/artists/by-stage-name sin stageName produce MissingServletRequestParameterException → 500 ErrorResponse, no 400. Otros @RequestParam obligatorios comparten el camino.
- **Mínimo:** handler para parámetro requerido ausente y tests de todas las familias de entrada inválida. Revisar también excepciones estándar de método/media no soportados para no transformarlas indiscriminadamente en 500. No se enumeran como fallos ejecutados adicionales sin probe.

### A07 — P2 / regresión HTTP y matriz: pruebas que no prueban lo que afirman

- **Contrato:** Controladores CTRL-010, §21/QT y DoD; Seguridad §§17.2/17.4.
- **Dónde:** `src/test/java/com/pulsepass/pass/controller/VenueControllerTest.java:81-86` prueba 404 en un método llamado returns500. `security/AuthorizationMatrixTest.java:375-389` cuenta una lista literal de 20 IDs, no requests reales. LoginHttpTest.java:327-337 no verifica el «neverInvokesBusiness» de su nombre.
- **Impacto:** eliminar rutas/tests o romper handler500 no necesariamente rompe esas aserciones. Faltan negativos STAFF/USER que habrían descubierto A02/A03.
- **Mínimo:** prueba real de excepción inesperada500, matriz parametrizada de rutas/actores/resultados derivados de ejecución y ausencia de efectos/interacciones cuando se deniega. No borrar regresiones para mantener el verde.
- **Matiz de metodología:** ControllerHttpSupport.java:48-50 usa aplicación completa/Testcontainers y no quedan slices WebMvcTest/MockitoBean ni verificaciones de Service/Content-Type como pedía Fase3. **Fase4 sí permite SpringBootTest según escenario**: no se considera su uso un bug, pero debe documentarse esa sustitución y recuperar la evidencia contractual perdida. No afirmar que integración completa demuestra aislamiento.

### A08 — P2 / Flyway y validate: garantía declarada, evidencia insuficiente

- **Contrato:** Persistencia QT-001/002, FR-EVT-006; Seguridad DoD esquema nuevo y ya migrado.
- **Dónde:** `src/test/java/com/pulsepass/pass/FlywayMigrationTest.java:44-54,68-73`.
- **Actual:** test de contexto solo verifica beans; test V3 **vacío**. No comprueba versión/catálogo, longitud/nullability de streaming_url ni que Hibernate efectivamente valide. No existe el test fuente de esquema existente citado por reportes históricos.
- **Mínimo:** catálogo de V3, versiones Flyway y propiedades efectivas; integración V3 con filas legadas → V4 que compruebe IDs/FK/datos/fecha y no passwords por defecto; reejecución sin pendientes aparte. No editar V1–V4 ni usar repair/reset.

### A09 — P2 / persistencia: constraints y relaciones no están plenamente afirmados

- **Contrato:** Persistencia §§8,11,17,21; FR-VEN-003, FR-TKT-001/003 y AC/QT asociados.
- **Dónde:** `VenueRepositoryTest.java:29-62`, `TicketRepositoryTest.java:95-103,176-184`, `UserProfileTest.java:39-49`, `SecurityCredentialsTest.java:110-117`.
- **Actual:** capacidad sin caso0/negativo; precio null no prueba CHECK negativo; igualdad Ticket solo por ticketCode no acredita user/event; tamaño de perfiles no acredita campos/navegación; rol null no prueba CHECK de rol desconocido. El DDL sí contiene esas restricciones.
- **Mínimo:** asserts explícitos y round-trip con flush/clear; casos de rangos, FK, enum/rol inválido no nulo y catálogo SQL cuando se busca probar PostgreSQL, no solo ORM. No exigir nuevas reglas de negocio.

### A10 — P2 / búsquedas: cobertura parcial de filtros/orden/DISTINCT y escenario de relaciones

- **Contrato:** Persistencia AC-003/006/007/008, FR-SRC y QT-003..008.
- **Dónde:** `EventRepositoryTest.java:117-183`, `TicketRepositoryTest.java:124-163`.
- **Actual:** casos positivos simples no discriminan todos los filtros; faltan tres artistas sin par duplicado, artista en varios eventos, distractores de ciudad/estado/evento, múltiples fechas para ordenar y varios matches para DISTINCT. Conteo de ventas no cubre todo el conjunto de estados/distractores.
- **Mínimo:** ampliar fixtures/aserciones existentes. No marcar consultas correctas como código roto por una brecha de test.

### A11 — P2 / servicios: addArtist carece del unit test expresamente requerido

- **Contrato:** Servicios §49 FR-SVC-007 y BR-EVENT-010/011; §42.
- **Dónde:** `src/test/java/com/pulsepass/pass/service/impl/EventServiceImplTest.java:57-234`; implementación `service/impl/EventServiceImpl.java:149-166`.
- **Actual:** ocho unitarios existentes, ninguno invoca addArtist. Hay HTTP positivo de un artista, pero no reemplaza sus unitarios de negocio/rechazo.
- **Mínimo:** éxito, duplicado, CANCELLED/FINISHED, recursos inexistentes y never save en inválidos.

### A12 — P2 / dinero: política real de precios no probada

- **Contrato:** Servicios §28 exige estrategia encapsulada y probada.
- **Dónde:** `src/main/java/com/pulsepass/pass/service/PriceCalculator.java:23-36`; TicketServiceImplTest.java:53-54 usa mock.
- **Actual:** el cálculo real se ejecuta indirectamente en HTTP, pero no se afirman importes ni los cuatro tipos. Un multiplicador erróneo puede pasar la suite.
- **Mínimo:** un test puro del componente real para GENERAL/STUDENT/VIP/BACKSTAGE, comparación BigDecimal y no negatividad. No crear PricingService ni interfaces adicionales.

### A13 — P2 / unitarios: transiciones afirmadas sobre DTO prefabricado, no sobre entidad

- **Contrato:** Servicios TEST-EVENT-007, TEST-TICKET-001/009/011 y guía sesión13.
- **Dónde:** `EventServiceImplTest.java:197-214`; `TicketServiceImplTest.java:87-107,292-309,343-363`.
- **Actual:** mapper/save devuelve fixture con estado esperado; se puede eliminar la mutación real y conservar el verde del unitario. Hay HTTP integrado que sí aporta evidencia de algunas transiciones; no se declara ausencia total de cobertura.
- **Mínimo:** afirmar estado de la entidad y capturar el argumento guardado, manteniendo asserts de DTO/mapping por separado. Capturar también DRAFT inicial y PAID real de creación.

### A14 — P2 / registro: AuthService duplica UserService y falta prueba real de atomicidad

- **Contrato:** Seguridad §§3.2,7.1,9.3,17.3 y NFR-SEC-004.
- **Dónde:** `AuthServiceImpl.java:131-177`; `UserServiceImpl.java:39-72`; `service/AuthService.java:14-16` afirma delegación inexistente. `AuthServiceImplTest.java:162-196` llama atómico a un test Mockito.
- **Actual:** flujo HTTP sí es transaccional por lectura y crea credencial/USER; no se demostró rollback al fallar una escritura final. Las reglas comerciales están duplicadas.
- **Mínimo:** orquestar la creación/reglas con UserService en la transacción exterior, sin nuevo framework; test integrado de éxito con hash verificable/rol y fallo tardío seguido de consulta desde otra transacción. No confundir rollback del propio test con atomicidad del servicio.

### A15 — P2 / filtro: duplicados Authorization ambiguos y unitario requerido ausente

- **Contrato:** Seguridad §17.1, entrada de doble header sospechoso.
- **Dónde:** `JwtAuthenticationFilter.java:63-68,85-98`; no existe JwtAuthenticationFilterTest.
- **Actual demostrado por subauditor:** getHeader toma primero; dos headers separados producen resultado según el orden. Debe existir token válido para autenticar: no es falsificación sin credenciales.
- **Mínimo:** definir/rechazar duplicados y probar ambos órdenes; cubrir sin/válido/inválido y contexto preexistente sin sobrescritura insegura. El PRD enumera escenario, no define una política detallada; acordar tratamiento consistente.

### A16 — P2 / NFR explícitos de seguridad incompletos

- **Contrato:** Seguridad §16 NFR-SEC-003,006,008.
- **Dónde:** `AuthServiceImpl.java:103-119`, RestAuthenticationEntryPoint.java:45-59, RestAccessDeniedHandler.java:44-58; SecurityConfig.java:49-136; CustomUserDetailsService.java:120-150.
- **Falta:** auditoría de login correcto/fallido y denegaciones **con request ID**; política **CORS parametrizada**; medición de carga/consultas de autenticación.
- **Precisión:** no configurar CORS no equivale a abrir todos los orígenes; no se demostró un N+1 ilimitado ni se inventa conteo SQL. Sí hay relecturas potenciales y falta la medición exigida.
- **Mínimo:** logs seguros/correlación acotados, configuración restrictiva de CORS con preflight probado y medición reproducible. Estos tres son obligatorios en Fase4 aunque el ejemplo simplificado no los incluya; rate limiting/rotación automática siguen fuera de alcance.

### A17 — P2 / esquema literal: PK de roles no es compuesta

- **Contrato:** Seguridad §§9.1/9.2.
- **Dónde:** `src/main/resources/db/migration/V4__security_credentials_roles.sql:73-85`; domain/UserRole.java:22-36.
- **Actual:** PK id surrogate + UNIQUE(user_id,role). **La unicidad funcional sí está protegida**; no es vulnerabilidad de duplicados, pero no es PK compuesta literal.
- **Mínimo:** registrar aceptación explícita de equivalencia o planificar migración posterior con adaptación JPA/repository/tests. No reescribir V4 aplicada para satisfacer literalidad.

### A18 — P2/P3 / documentación operativa y decisiones históricas

- **Contrato:** Seguridad §§9.2,16 NFR007,22; DoD README de capas anteriores.
- **Dónde:** README.md:97-113,133-165,167-176,289-319; MatrizAutorizacion.md:27,111-132.
- **Actual:** ejemplo correcto spring.* contradice archivo real (A01). Se nombran roles/proceso controlado pero faltan pasos concretos de provisión STAFF/ADMIN/activación legada; no se demostró HTTPS externo. Matriz secundaria conserva dueño de registro antiguo y decisiones ya resueltas.
- **Mínimo:** documentar mecanismo seguro, prerequisitos y evidencias; marcar documentos de etapa como históricos. TLS debe demostrarse antes de uso externo, no imponerse a MockMvc/localhost. No crear cuentas privilegiadas por defecto ni passwords compartidas.

### A19 — P3 / nombre de contrato mínimo Java

- **Contrato:** Servicios §21 nombra findPaidTicketsByEvent.
- **Dónde:** `service/TicketService.java:16`, `service/impl/TicketServiceImpl.java:173`, `controller/EventController.java:72` usan findPaidTicketByEvent.
- **Actual:** HTTP y consumidores internos son consistentes; no hay bug funcional demostrado. Hay diferencia literal para consumidor Java escrito contra el PRD.
- **Mínimo:** aceptar explícitamente variante o alinear interfaz/implementación/caller; no cambiar ruta ni comportamiento. Es posterior a seguridad/configuración en prioridad.

## 6. Riesgos preventivos, no fallos activos demostrados

1. **Cascada credencial→usuario:** UserCredential.java:39-42 tiene CascadeType.ALL, que incluye REMOVE hacia el padre. Una eliminación futura de credencial podría intentar borrar usuario/perfil o fallar por FKs. No hay endpoint de borrado ni pérdida observada. Probar política antes de habilitar esa operación; @MapsId no obliga universalmente a propagar REMOVE.
2. **Aislamiento de fixtures:** algunas pruebas repository confirman datos sin rollback uniforme; otras comparten códigos/semilla. La corrida actual pasó: no se reporta fallo por orden observado. Añadir comprobación de repetición/orden si se quiere cerrar ese riesgo.
3. **TicketCode truncado:** TicketServiceImpl.java:115 usa ocho caracteres hex del UUID; UNIQUE preserva integridad pero una colisión podría rechazar compra. No se observó colisión. Conservar más UUID es hardening pequeño, no inventario concurrente.
4. **BCrypt login >72 bytes:** registro ya limita bytes; login no. El subauditor confirmó alias por truncamiento con un prefijo válido de 72 bytes. No permite adivinar ese prefijo. Aplicar límite de bytes en login y revisar código HTTP del rechazo multibyte de registro es robustez adicional.
5. **Logging de inesperados:** GlobalExceptionHandler.java:131-140 retorna500 genérico sin registrar causa. No filtra stack al cliente, pero dificulta diagnóstico. Registrar excepción de manera segura, sin cuerpos/credenciales.
6. **Tiempo/fixtures:** fecha de nacimiento fija del caso menor y eventos relativos al reloj pueden dejar de representar el escenario al avanzar el calendario. Usar datos relativos/coherentes; no meter Clock en toda la aplicación si no hace falta.
7. **Rendimiento/índices:** índice adicional user_roles.user_id puede ser redundante con UNIQUE(user_id,role); no cambiar migraciones por intuición ni afirmar coste sin medir.
8. **Hardening productivo diferido:** rate limiting, MFA, refresh, revocación individual, rotación automática, secret manager y asignación STAFF-evento no son requisitos del MVP. Deben permanecer declarados como límites, no «funciones faltantes» inventadas.

## 7. Contradicciones resueltas sin editar PRD

| Tema | Lectura adoptada |
|---|---|
| Seguridad fuera de alcance en fases1–3 | Fase4 la incorpora deliberadamente: evolución válida, no eliminarla |
| SOLD_OUT manual en persistencia, automático en servicios | Fase2 añade explícitamente la actualización; se conserva |
| eventDate DATE en V1 y LocalDateTime en contrato posterior | V4 hace ALTER a TIMESTAMP; esquema final coherente, no cambiar V1 |
| Capacidad5000 frente3 en ejemplos | Fixtures por capa; no una única constante global obligatoria |
| ErrorResponse «seis campos» en Seguridad §3.4 | Antecesor y propio §13 enumeran cinco; mantener cinco, no inventar campo |
| Guía recomienda paquetes por feature | PRD PulsePass prescribe capas; no reestructurar proyecto por ejemplo University API |
| Guía usa sub=username/roles en claims/in-memory/ORGANIZER | PRD específico manda sub=ID persistente, roles vigentes, PostgreSQL y USER/STAFF/ADMIN |
| AuthenticationManager frente implementación directa | PRD permite equivalente compatible; UserDetailsService+PasswordEncoder no es defecto por sí mismo |
| Perfil obligatorio en diagrama frente máximo uno en texto | Aplicar máximo uno/relación opcional del requisito explícito; no añadir trigger |
| minimumAge int frente Integer | En runtime actual omisión/null se rechazan; no declarar aceptación silenciosa sin ejecutar |

No se encontró evidencia suficiente para acusar una reescritura reciente de V1–V3 por la fecha del evento. La compatibilidad con checksums de una base desplegada sigue condicionada a su historial real, no inspeccionado. Nunca se aconseja repair a ciegas.

## 8. Secuencia mínima de corrección propuesta — NO ejecutada

1. **Configuración/arranque:** A01, configuración efectiva validate y prueba aislada sin ocultar YAML principal.
2. **Seguridad funcional:** A02/A03/A04, primero tests negativos que fallen, luego políticas y JWT. Mantener rutas/DTO comerciales.
3. **Token/filtro/HTTP:** A05/A06/A15 y prueba real500; cerrar casos de entrada malformada sin filtrar detalles internos.
4. **Persistencia/atomicidad:** A08/A09/A14, esquema nuevo/upgrade poblado y rollback real. No tocar migraciones aplicadas.
5. **Servicios/evidencia:** A10–A13 y matriz real A07, reforzando tests existentes en vez de multiplicar abstracciones.
6. **NFR/documentación:** A16/A18; medir antes de optimizar y documentar límites productivos. Resolver aceptación estructural A17; nombre A19 al final.
7. Ejecutar otra vez suite completa y los nuevos negativos; solo entonces reevaluar DoD. Un BUILD SUCCESS previo no cierra requisitos que nunca se afirman.

## 9. Límites y entregables

**No se comprobó:** base persistente/history de Flyway del usuario, despliegue/TLS real, carga SQL real, pentest externo, auditoría exhaustiva de dependencias ni todo el historial de secretos. No se evaluó el conocimiento oral del estudiante ni se implementaron ejercicios auxiliares de CourseHub.

**Riesgo vigente:** los permisos indebidos y validaciones JWT fuera del contrato están reproducidos con fixtures; la configuración documentada no enlaza persistencia. No considerar el sistema listo para exponerlo a terceros por tener 264 tests verdes.

Los anexos conservan el análisis estático detallado por capa: todos los FR/NFR/AC/QT de persistencia, todas las reglas de servicios y tests mínimos, las 20 rutas por rol/ownership y el DoD de seguridad. Cuando un subinforme dice «no ejecutado por este subagente», se refiere a su trabajo paralelo, no contradice la **ejecución consolidada actual de §3**. Tampoco sus marcas de cobertura parcial se convierten en fallos de código automáticamente.

---

# Anexos técnicos de trazabilidad


---

# Anexo A — Persistencia: trazabilidad completa

# Auditoría de persistencia de PulsePass

## 1. Dictamen ejecutivo

**POR CORREGIR.** El modelo, los ocho repositories y las migraciones acumuladas V1–V4 contienen las capacidades funcionales de persistencia del PRD. No encontré una consulta funcional obligatoria ausente. Eso **no** equivale a cumplimiento integral ni a pruebas aprobadas: hay una configuración de persistencia mal ubicada, pruebas que no comprueban lo que anuncian y escenarios obligatorios cuya evidencia es parcial o inexistente.

**CRÍTICO contractual / prioridad P1:** en `src/main/resources/application.yml:21-48`, `datasource`, `jpa` y `flyway` quedaron bajo `pulsepass`, no bajo `spring`. Por tanto, las propiedades declaradas **no configuran** el datasource de Spring Boot, `ddl-auto=validate` ni `open-in-view=false`. Una suite con `@ServiceConnection` puede ocultar el problema del datasource. No se ejecutó el arranque para demostrar su excepción concreta; el defecto de namespace sí es visible y determinista por lectura. No se declara un incidente P0 ni daño de datos observado.

**COMPLETO por inspección, no por ejecución:** entidades y relaciones del MVP; PK/FK/UNIQUE/CHECK de V1; catálogo de cinco artistas de V2; `streaming_url VARCHAR(500)` nullable en V3; conversión de `event_date` y credenciales/roles en V4; consultas derivadas y JPQL requeridas; uso de PostgreSQL/Testcontainers en la infraestructura de integración.

**Resultado de ejecución:** no se ejecutó Maven, la aplicación, Flyway ni ningún test. La ejecución de suite queda a cargo del agente padre. Tampoco se conectó a la BD del usuario. No hay resultados nuevos de aprobación/reprobación.

## 2. Baseline, alcance y fuentes

- Repositorio: `C:/Users/57321/Desktop/pulse_pass`.
- Rama leída: `feature/rebuild-pulsepass`.
- HEAD: `5c9056f385f0f459f39b809029718db2b8f0ee98`.
- `git diff --name-only --diff-filter=U` no devolvió conflictos.
- Cambios ya existentes al inicio, no generados por esta auditoría: `EstadoSesion_Capa_Seguridad.md`, `MatrizAutorizacion.md`, `PlanAccion_Capa_Seguridad.md`, `error` y `guia/` sin seguimiento. No se modificaron.
- Fuente autoritativa: `PRD_PulsePass.md`, leído completo (495 líneas).
- Evolución leída completa: `PRD_PulsePass_Capa_Servicios.pdf`, `PRD_PulsePass_Capa_Controladores_v2.pdf`, `PRD_PulsePass_Capa_Seguridad_Spring_Security_JJWT.pdf`.
- Guía leída completa: `guia/Spring Framework, Spring Boot y arquitectura del Backend.md` (3665 líneas).
- Se leyeron completos los 13 archivos de `domain/`, los ocho de `repository/`, las cuatro migraciones, los ocho archivos de pruebas de persistencia del paquete raíz y `PostgresContainerSupport`. También `pom.xml`, README, configuración YAML y pruebas auxiliares pertinentes de registro, login, carga de identidad, visibilidad y soporte HTTP.
- No se abrió `.env`, `.env.properties` ni archivos locales de secretos. Las constantes de prueba explícitamente ficticias no se reproducen en este informe.
- Servicios/seguridad se examinaron únicamente donde era necesario para trazar accesos a persistencia y cobertura. La evaluación completa de esas capas corresponde a otros auditores.

### Leyenda de evidencia

- **I**: implementado/verificado mediante inspección estática.
- **T**: existe prueba con una aserción pertinente; **no significa ejecutada** en esta auditoría.
- **P**: prueba parcial, indirecta o incapaz de acreditar todo el requisito.
- **F**: falta evidencia/prueba requerida o existe desviación.
- **NE**: no ejecutado/no acreditado dinámicamente.

Los paths del informe son relativos al repositorio salvo el archivo scratch. Para aligerar las tablas, `D/` equivale a `src/main/java/com/pulsepass/pass/domain/`, `R/` a `src/main/java/com/pulsepass/pass/repository/`, `T/` a `src/test/java/com/pulsepass/pass/` y `M/` a `src/main/resources/db/migration/`. Los hallazgos detallados incluyen paths completos relativos.

## 3. Hallazgos priorizados

### PER-01 — P1 — Configuración efectiva de persistencia ausente por indentación

**Tipo:** defecto de configuración; incumplimiento actual, no mera falta de prueba.

**Contrato:** PRD Persistencia §13 (`spring.jpa.hibernate.ddl-auto=validate`), QT-002 (§17), DoD §21; guía §§41, 50–52 y 55–58.

**Evidencia:**

- `src/main/resources/application.yml:18-26` abre `spring.config`, después una nueva raíz `pulsepass.security.jwt`.
- `src/main/resources/application.yml:28-48` sitúa `datasource`, `jpa`, `flyway` con la misma indentación que `security`, dentro de `pulsepass`.
- Claves reales reconstruidas mediante un probe de indentación: `pulsepass.datasource.url`, `pulsepass.jpa.hibernate.ddl-auto`, `pulsepass.jpa.open-in-view`, `pulsepass.flyway.locations`; **no** `spring.datasource.url` ni `spring.jpa.hibernate.ddl-auto`.
- `src/test/java/com/pulsepass/pass/PostgresContainerSupport.java:17-22` aporta datasource por `@ServiceConnection`, por lo que los tests no ejercitan necesariamente la resolución de `DB_URL`/`DB_USER`/`DB_PASSWORD` del YAML de producción.
- `src/test/resources/application.yml:12-18` solo configura JWT ficticio; no exige `spring.jpa.hibernate.ddl-auto=validate`.
- `git show 5b06d19 -- src/main/resources/application.yml` muestra la introducción de la raíz `pulsepass` delante del bloque de persistencia existente: regresión localizada.

**Comportamiento/impacto:** Spring Boot no enlaza esos bloques con su autoconfiguración. Sin un override externo correcto, el datasource declarado no existe para Boot. Con PostgreSQL no embebido, la presencia de un texto `ddl-auto: validate` bajo otra raíz no activa validación. Flyway puede seguir ejecutándose por sus defaults (enabled y ubicación convencional); no afirmar que deja de migrar solo por este defecto. `open-in-view=false` tampoco queda aplicado. El README afirma lo contrario en `README.md:97-113` y documenta variables DB que aquí no se consumen correctamente (`README.md:141-145`).

**Corrección mínima propuesta:** volver a colocar **los bloques** datasource/JPA/Flyway bajo `spring`, conservando JWT bajo `pulsepass`. No editar migraciones, entidades ni PRDs. Añadir una comprobación del valor efectivo de `spring.jpa.hibernate.ddl-auto`/propiedades JPA, no solo del texto YAML. Probar configuración/arranque con PostgreSQL aislado y variables ficticias; nunca usar la BD personal para esta comprobación.

**Estado de verificación:** defecto estático confirmado; arranque y propiedades efectivas en ejecución NE. No se presupone el contenido del entorno ni de archivos de secretos, que podrían introducir overrides.

### PER-02 — P2 — Tests de Flyway/Hibernate dan una garantía mayor que la que realmente verifican

**Tipo:** defecto de evidencia / cobertura obligatoria.

**Contrato:** NFR-002/003, QT-001/002, FR-EVT-006, §15, §17 y DoD §21 del PRD de Persistencia.

**Evidencia:**

- `src/test/java/com/pulsepass/pass/FlywayMigrationTest.java:44-54` solo verifica que seis repositories no sean null. No inspecciona configuración efectiva, esquema, historial ni versión de Flyway.
- `src/test/java/com/pulsepass/pass/FlywayMigrationTest.java:68-73` tiene un método `@Test` **sin instrucciones ejecutables ni aserciones** para V3.
- Los comentarios de `FlywayMigrationTest.java:16-18,45-47,70-72` asumen que el contexto usa validate; PER-01 y el YAML de test no lo garantizan.
- `src/test/resources/application.yml:12-18` solo contiene configuración JWT. Un `application.yml` homónimo de test no es un `application-test.yml` que herede automáticamente cada propiedad del archivo main homónimo; no usar esa suposición como evidencia de validate.

**Impacto:** un contexto puede cargar aunque el esquema no se haya validado, y la prueba vacía no detecta eliminar V3, cambiar nullability o longitud. La columna mapeada en JPA no prueba su tipo/longitud/nullable mediante una simple inyección de repositories.

**Corrección mínima:** exigir validate explícitamente en configuración de integración y comprobarlo; convertir el método vacío en una lectura de catálogo PostgreSQL para `streaming_url` (varchar, longitud 500, nullable), más round-trip de null/valor. Consultar la API de Flyway para versiones aplicadas. SQL de catálogo **en un test de integridad** no contradice la prohibición de SQL nativo para consultas funcionales del taller.

**Estado:** F en QT-002; P en QT-001 y FR-EVT-006. No confundir defecto de test con falta de la migración: V3 sí existe.

### PER-03 — P2 — No hay prueba de migración sobre esquema preexistente en el HEAD auditado

**Tipo:** cobertura obligatoria de la evolución de seguridad; no prueba de que V4 sea incorrecta.

**Contrato:** PRD Seguridad §§3.1, 9.2, 17.3 y DoD §22 (esquema nuevo y ya migrado; preservar datos anteriores); NFR-002/003 del PRD base.

**Evidencia:**

- `src/main/resources/db/migration/V4__security_credentials_roles.sql:20-21` convierte DATE a TIMESTAMP.
- El único test fuente de migraciones presente es `src/test/java/com/pulsepass/pass/FlywayMigrationTest.java:23-73`, sin upgrade controlado ni catálogo V4.
- `git ls-files src/test/java` y lectura del directorio fuente: **no existe** `src/test/java/com/pulsepass/pass/FlywayMigrationOnExistingSchemaTest.java` en este HEAD.
- `git log --all -- '*Flyway*'` sí descubre commit **8491cef588389b96112bd260aed9edf9f5478ec7**, presente en `feature/pulsepass-security`/remoto correspondiente, **no en la rama auditada**. Su prueba llama migrate después del arranque a última versión y comprueba cero pendientes y catálogo: sería evidencia de reejecución V4→V4, **no** de transición V3→V4 con filas legadas. No atribuirla a HEAD ni describirla como upgrade poblado.

**Impacto:** no se detectan regresiones de actualización ni pérdida/modificación inesperada de filas existentes. Una base vacía que arranca no cierra esta exigencia.

**Corrección mínima:** añadir un test aislado que migre hasta V3, inserte evento DATE/usuario/perfil/ticket legados, ejecute V4 y compruebe identidad, FKs, datos, fecha convertida y ausencia de contraseña predeterminada. Añadir reejecución sin pendientes como escenario aparte. Si se recupera el test de otra rama, aclarar su alcance y no usar sus comentarios como prueba de migrar desde V3.

### PER-04 — P2 — Rango de capacidad, precio y referencias obligatorias no están demostrados contra PostgreSQL

**Tipo:** implementación presente, cobertura obligatoria ausente/parcial.

**Contrato:** FR-VEN-003, FR-TKT-001/003, FR-EVT-003/004, FR-TKT-004/005; NFR-001; §§8,17,21 del PRD base.

**Evidencia de implementación:**

- `src/main/resources/db/migration/V1__create_schema.sql:15-18` impone capacidad > 0.
- `V1__create_schema.sql:29-55` restringe categoría/estado del evento.
- `V1__create_schema.sql:117-147` restringe tipo/estado, precio >= 0 y FKs de ticket.
- `V1__create_schema.sql:33-36,73-82,105-108` protege venue, N:M y perfil.

**Evidencia de brecha:**

- `src/test/java/com/pulsepass/pass/VenueRepositoryTest.java:29-62`: no prueba capacidad cero o negativa ni capacidad recuperada; el test `shouldSaveAndRetrieveVenueById` consulta por código, no por ID.
- `src/test/java/com/pulsepass/pass/TicketRepositoryTest.java:176-184` prueba **null**, no precio negativo. NOT NULL no prueba `CHECK price >= 0`.
- No hay tests raíz que inserten ticket con user/event inexistente o ausente, evento sin venue, ni valores SQL fuera de los catálogos. Los constructores Java con enum no pueden producir un valor textual desconocido para probar el CHECK SQL.

**Impacto:** eliminar o relajar un CHECK/FK no tiene una aserción específica que necesariamente falle. No se debe marcar FR-VEN-003 o FR-TKT-003 como verificados por un test de unicidad o de null.

**Corrección mínima:** tests parametrizados de capacidad 0/-1/1 y precio negativo/0/positivo; una escritura SQL controlada por caso para CHECK de catálogos y FK inválida, aislando transacciones de error. Verificar constraint/SQLSTATE cuando se quiera afirmar rechazo de PostgreSQL y no solamente una validación previa de Hibernate. No añadir lógica de negocio a repositories.

### PER-05 — P2 — Relaciones y búsquedas: aserciones positivas insuficientes para aceptación completa

**Tipo:** cobertura funcional parcial; no consulta incorrecta demostrada.

**Contrato:** AC-002/003/006/007/008, FR-USR-004, FR-SRC-001..004 y QT-003..008; PRD base §§11,17,21.

**Evidencia:**

- `src/test/java/com/pulsepass/pass/TicketRepositoryTest.java:95-103`: `shouldLinkTicketToUserAndEvent` solo compara `Ticket.equals`, que se define **exclusivamente por ticketCode** en `src/main/java/com/pulsepass/pass/domain/Ticket.java:115-124`. No comprueba user, event, price, type, status ni purchaseDate.
- `src/test/java/com/pulsepass/pass/UserProfileTest.java:39-49`: comprueba `findAll().hasSize(1)`, no los datos del perfil ni la navegación `User.getProfile()`.
- `src/test/java/com/pulsepass/pass/EventRepositoryTest.java:117-135`: asocia **dos**, no tres artistas; no intenta duplicar el par ni recarga para contar filas.
- `EventRepositoryTest.java:138-151`: un solo evento para un artista; no demuestra múltiples eventos ni unicidad del resultado en presencia de varios matches.
- `EventRepositoryTest.java:154-167`: solo un caso positivo de ciudad+artista; faltan eventos distractores de otra ciudad/otro artista.
- `EventRepositoryTest.java:170-183`: una sola recomendación. Comprueba coincidencia ignore-case, pero no exclusión por fecha/estado/ciudad, DISTINCT con dos artistas coincidentes ni orden entre dos fechas.
- `TicketRepositoryTest.java:124-136`: PAID+RESERVED, sin CANCELLED/USED ni otro evento distractor.
- `TicketRepositoryTest.java:138-163`: un futuro y un pasado; no acredita orden cronológico entre al menos dos futuros ni frontera igual a fecha.
- `EventRepositoryTest.java:72-89`: PUBLISHED frente a DRAFT, no CANCELLED ni restantes estados. Las pruebas HTTP de visibilidad añaden DRAFT, no reemplazan todos los negativos del criterio.

**Impacto:** tests verdes podrían coexistir con mappings rotos, filtros incompletos o pérdida de DISTINCT/ORDER BY. No declarar que toda la relación se verificó por comparar códigos.

**Corrección mínima:** ampliar fixtures existentes con negativos/distractores y aserciones explícitas de IDs/atributos; `flush` + `EntityManager.clear` + recarga cuando se quiera probar round-trip, no identidad del primer nivel de caché. No crear una arquitectura nueva de tests.

### PER-06 — P2 — Riesgo de dependencia del orden entre tests por escrituras confirmadas

**Tipo:** riesgo estático de aislamiento; fallo de suite **no observado** aquí.

**Contrato:** NFR-004/005, estrategia §17 y DoD reproducible; guía de separación/testabilidad.

**Evidencia:**

- `src/test/java/com/pulsepass/pass/VenueRepositoryTest.java:19-26` y `UserRepositoryTest.java:23-30` no son transaccionales a nivel de clase; cada save de repository puede confirmar su transacción y quedan filas después del último método.
- `UserRepositoryTest.java:34-94` reutiliza `andrea`, `carlos` y otros identificadores comunes.
- `src/test/java/com/pulsepass/pass/TicketRepositoryTest.java:69-91` inserta `andrea`/`carlos` sin limpiar/buscar previamente. Un estado confirmado de otra clase que comparta contexto/contenedor puede colisionar.
- `src/test/java/com/pulsepass/pass/ArtistRepositoryTest.java:35-44` confirma el original y depende de una limpieza manual **después** de la aserción: si esta falla, queda el artista; `FlywayMigrationTest.java:59` exige count global exactamente 5.
- El datasource static de `PostgresContainerSupport.java:17-22` es compartido; no asumir un PostgreSQL nuevo por método/clase. Las diferentes configuraciones de contexto pueden añadir aislamiento parcial, pero no son garantía de limpieza para todas las clases.

**Impacto:** suites o reejecuciones pueden fallar por datos residuales y no por la consulta/constraint que se pretendía probar. `deleteAllInBatch` de solo el padre además no constituye limpieza general segura si existen hijos confirmados. No afirmar que todos los usos de bulk delete son incorrectos: aquí se señala el alcance limitado del borrado y la falta de rollback uniforme.

**Corrección mínima:** preferir rollback de test con códigos propios; para un error esperado que aborta la transacción, aislarlo en su caso. Evitar commits de fixtures compartidos y no borrar la semilla global sin necesidad. Probar clases en diferente orden en una ejecución aislada, sin tocar BD del usuario.

### PER-07 — P2, contractual — user_roles preserva unicidad, pero no la PK compuesta literal del PRD de Seguridad

**Tipo:** desviación estructural documentada con equivalencia funcional de unicidad, **no** defecto de duplicados.

**Contrato:** PRD Seguridad §§9.1 y 9.2 pide `PK(user_id, role)`. A diferencia de `event_artists` en PRD base §7, allí no aparece expresamente la alternativa «o UNIQUE equivalente».

**Evidencia:**

- `src/main/resources/db/migration/V4__security_credentials_roles.sql:73-85`: PK de `id`; `UNIQUE(user_id, role)`; CHECK catálogo y FK correctos.
- `src/main/java/com/pulsepass/pass/domain/UserRole.java:22-36`: declara conscientemente UNIQUE como representación de PK compuesta; JPA usa id generado.
- `src/main/java/com/pulsepass/pass/repository/UserRoleRepository.java:11`: `JpaRepository<UserRole, Long>`.
- `src/test/java/com/pulsepass/pass/SecurityCredentialsTest.java:100-107` prueba el duplicado por UNIQUE; no una PK compuesta real.
- `README.md:293` mantiene esa equivalencia en su descripción.

**Impacto:** se satisface el objetivo de no repetir rol por usuario, pero no la forma estructural literal. No es correcto informar que el catálogo real tiene PK compuesta porque existe una restricción UNIQUE sobre dos columnas.

**Corrección mínima según decisión de aceptación:** registrar aprobación explícita de la equivalencia funcional, sin tocar código ni PRD; si se exige literalidad, planificar **migración posterior** y adaptación de identidad JPA/repositorio/tests a clave compuesta. No reescribir V4 aplicada para lograr una PK «bonita». La integridad de migraciones históricas tiene prioridad sobre una corrección destructiva de estilo.

### PER-08 — P2 de evidencia / riesgo latente — Credenciales: cobertura confunde catálogo y nulabilidad; cascada ascendente requiere cautela

**Contrato:** PRD Seguridad §§9.1/9.2, 17.3 y preservación de User en §3.1.

**Evidencia:**

- `src/test/java/com/pulsepass/pass/SecurityCredentialsTest.java:110-117`, llamado `shouldRejectRoleOutsideCatalogue`, pasa `null`: verifica NOT NULL/Hibernate, **no** rechazo SQL de un valor textual fuera de USER/STAFF/ADMIN.
- `SecurityCredentialsTest.java:49-62` persiste un texto de prueba con prefijo de BCrypt. El prefijo no demuestra un hash verificable. `CustomUserDetailsServiceTest.java:165-173` sí prueba encode/matches y `security/LoginServiceTest.java:74-104` usa hashes reales de prueba guardados y login real; por tanto **no** se reporta ausencia total de test de BCrypt.
- `src/main/java/com/pulsepass/pass/domain/UserCredential.java:39-42` tiene `cascade = CascadeType.ALL` desde credencial hacia User, incluida operación REMOVE.
- No hay test que delimite qué pasa al eliminar una credencial. Los borrados bulk de fixtures evitan esa cascada y no la comprueban.

**Impacto:** el test de catálogo puede pasar aunque se quite el CHECK. Adicionalmente, borrar una credencial con la operación JPA delete puede intentar borrar su usuario y, por la cascada User→perfil, el perfil; si existen otros hijos, las FKs pueden bloquearlo. No hay endpoint de borrar credencial auditado ni pérdida observada: es riesgo latente de ciclo de vida, no incidente activo ni requisito de añadir un endpoint.

**Corrección mínima:** prueba SQL del rol desconocido; test de borrado de credencial con usuario preservado antes de decidir la cascada necesaria. No dar por universal que `@MapsId` requiere `CascadeType.ALL`: la identidad compartida no obliga a propagar REMOVE al agregado padre. Ajustar únicamente las operaciones necesarias si la prueba confirma ese riesgo. Este ajuste no exige tocar DDL.

### PER-09 — P2 — Cobertura real de registro atómico no está acreditada por respuesta 201 o mocks

**Tipo:** implementación transaccional visible, prueba de rollback real faltante; coordinación con auditor de seguridad.

**Contrato:** PRD Seguridad FR-SEC-AUTH-001, NFR-SEC-004, §§9.3,17.3 y DoD §22.

**Evidencia:**

- `src/main/java/com/pulsepass/pass/service/impl/AuthServiceImpl.java:135-177` sí usa `@Transactional` y guarda User, perfil, credencial y rol USER.
- `src/test/java/com/pulsepass/pass/controller/UserControllerTest.java:41-61` verifica HTTP 201 y campos del DTO; no cuenta las cuatro filas ni provoca un fallo tardío.
- La clase hereda `@Transactional` de `controller/ControllerHttpSupport.java:48-50`; un rollback exterior al final del test no demuestra la atomicidad propia de una llamada de servicio que falla.
- Tests Mockito del registro no pueden demostrar rollback de PostgreSQL. Los tests de login guardan fixtures válidas manualmente y tampoco prueban rollback del registro.

**Impacto:** existe la anotación adecuada, pero no el escenario de integración requerido que detecte cuentas huérfanas si falla una escritura final.

**Corrección mínima:** integración con servicios reales que provoque un fallo después de crear User/perfil y consulte desde una nueva transacción que no quedaron usuario, perfil, hash ni rol. Mantenerlo en BD Testcontainers; no ampliar a concurrencia ni usar datos reales.

### PER-10 — P3 — JPQL en consultas triviales y comentarios de índices/documentación imprecisos

**Tipo:** mantenibilidad/recomendación académica; no resultado funcional erróneo.

**Contrato:** NFR-007 y estrategia §14 del PRD base; guía §58.

- `src/main/java/com/pulsepass/pass/repository/TicketRepository.java:23-28` usa JPQL para filtros email/email+status que §14 prefiere como Query Methods. `JOIN FETCH` aporta un motivo técnico razonable para elegir JPQL; `README.md:79-85` lo documenta genéricamente. No exigir reemplazo si se mantiene una justificación explícita de evitar carga adicional.
- `src/main/resources/db/migration/V4__security_credentials_roles.sql:91-98` dice que `user_roles.user_id` carece de índice propio, pero `UNIQUE(user_id, role)` ya genera un índice cuyo prefijo es user_id. El índice simple adicional puede ser redundante; no se midió coste ni se exige EXPLAIN para aprobar el MVP.
- La unicidad de email de V1 es exacta; `IgnoreCase` se implementa en consultas/validación de servicios. No inventar un requisito de índice funcional o de unicidad case-insensitive en PostgreSQL para la fase base, ni convertir este punto en una exigencia de concurrencia.

**Corrección mínima:** aclarar README/justificación cuando corresponda; si más adelante se elimina un índice redundante, hacerlo con migración nueva y medición. No reescribir V4 por comentarios ni por optimización no solicitada.

## 4. Checklist de requisitos funcionales — 30 FR

Todos los identificadores se extrajeron del PRD mediante expresión regular y se deduplicaron en código: 30 FR, ocho NFR, ocho AC y diez QT. La presencia de test es evaluación de su cuerpo fuente, no de su nombre ni un resultado de ejecución.

| ID / sección PRD | Implementación y evidencia | Prueba existente / valoración | Estado |
|---|---|---|---|
| FR-VEN-001 §4.1 | D/Venue.java:25-48,53-60; R/VenueRepository.java:11-15; M/V1__create_schema.sql:9-19 | T/VenueRepositoryTest.java:29-45 persiste y busca por código; no recupera por ID ni comprueba todos los atributos | I + P |
| FR-VEN-002 §4.1 | M/V1__create_schema.sql:11 UNIQUE NOT NULL | T/VenueRepositoryTest.java:52-61 duplica código con saveAndFlush | I + T |
| FR-VEN-003 §4.1 | M/V1__create_schema.sql:15-18 CHECK > 0 | Sin caso cero/negativo; PER-04 | I + F prueba |
| FR-VEN-004 §4.1 | R/EventRepository.java:32 navega venue.code | T/EventRepositoryTest.java:56-68; un venue/evento, falta distractor | I + P |
| FR-EVT-001 §4.2 | D/Event.java:34-66,82-94; M/V1__create_schema.sql:24-36; V4:20-21 alinea fecha | T/EventRepositoryTest.java:56-68,93-99; relación positiva, falta FK inválida y recarga de atributos | I + P |
| FR-EVT-002 §4.2 | M/V1__create_schema.sql:26 UNIQUE | T/EventRepositoryTest.java:102-114 saveAndFlush duplicado | I + T |
| FR-EVT-003 §4.2 | D/Event.java:51-53; D/EventStatus.java:3-8; V1:48-55 CHECK | Estados válidos usados; no se fuerza valor SQL fuera catálogo | I + F prueba negativa |
| FR-EVT-004 §4.2 | D/Event.java:47-49 STRING; D/EventCategory.java:3-10; V1:38-46 CHECK | Sin lectura SQL que confirme nombre persistido/catálogo inválido | I + F prueba directa |
| FR-EVT-005 §4.2 | R/EventRepository.java:30 order by fecha | T/EventRepositoryTest.java:72-89 positivo+orden+DRAFT; falta CANCELLED, SOLD_OUT, FINISHED | I + P |
| FR-EVT-006 §4.2 | D/Event.java:61-62; M/V3__add_streaming_url_to_event.sql:6-7 nullable varchar(500) | T/FlywayMigrationTest.java:68-73 vacío; no null/500/501 ni round-trip no-null | I + F prueba |
| FR-ART-001 §4.3 | D/Artist.java:25-42,47-51; V1:61-67; R/ArtistRepository.java:11-15 | T/ArtistRepositoryTest.java:24-26 semilla;35-37 persiste original, faltan atributos/recarga | I + P |
| FR-ART-002 §4.3 | V1:63 UNIQUE NOT NULL | T/ArtistRepositoryTest.java:35-41 duplicado | I + T |
| FR-ART-003 §4.3 | D/Event.java:68-74,150-157; D/Artist.java:41-42; V1:72-82 PK compuesta | T/EventRepositoryTest.java:118-134 usa dos artistas, no repetición ni cardinalidad tras clear | I + P |
| FR-ART-004 §4.3 | R/EventRepository.java:34-35 DISTINCT JOIN N:M | T/EventRepositoryTest.java:138-150 solo un evento por artista | I + P |
| FR-USR-001 §4.4 | D/User.java:28-45,50-54; V1:88-93; R/UserRepository.java:10-20 | T/UserRepositoryTest.java:34-50 persiste/consulta username/email | I + T parcial atributos |
| FR-USR-002 §4.4 | V1:90-91 UNIQUE username/email | T/UserRepositoryTest.java:78-94 prueba ambas duplicidades con saveAndFlush | I + T |
| FR-USR-003 §4.4 | D/UserProfile.java:39-41; D/User.java:41-42; V1:105-108 FK UNIQUE NOT NULL | T/UserProfileTest.java:52-64 impide dos perfiles | I + T |
| FR-USR-004 §4.4 | D/UserProfile.java:24-41,46-54; D/User.java:72-73 navegación inversa | T/UserProfileTest.java:40-48 solo tamaño global; no atributos/recuperación desde User | I + F prueba específica |
| FR-TKT-001 §4.5 | D/Ticket.java:49-55; V1:121-128 FK NOT NULL | T/TicketRepositoryTest.java:95-102 compara solo ticketCode; no FK inexistente/ausente | I + P débil |
| FR-TKT-002 §4.5 | V1:116 UNIQUE NOT NULL | T/TicketRepositoryTest.java:166-173 saveAndFlush duplicado | I + T |
| FR-TKT-003 §4.5 | D/Ticket.java:39-40 BigDecimal; V1:118,146-147 NUMERIC/CHECK | T/TicketRepositoryTest.java:177-183 solo null; no negativo/cero | I + F prueba rango |
| FR-TKT-004 §4.5 | D/Ticket.java:35-37 STRING; D/TicketType.java:3-7; V1:130-136 | GENERAL/VIP usados; no round-trip BACKSTAGE/STUDENT ni SQL inválido | I + P |
| FR-TKT-005 §4.5 | D/Ticket.java:42-44 STRING; D/TicketStatus.java:3-7; V1:138-144 | Fixtures PAID/RESERVED; otros en servicios/HTTP; falta comprobación SQL catálogo | I + P |
| FR-TKT-006 §4.5 | R/TicketRepository.java:19,23-28 email y status | T/TicketRepositoryTest.java:106-121 prueba email/status positivamente; falta mismo usuario con otro status y otro usuario con mismo status | I + P |
| FR-TKT-007 §4.5 | R/TicketRepository.java:33-34 PAID y eventCode, fetch user/event | T/TicketRepositoryTest.java:132-135 tamaño 2; falta otro evento y assert status/code | I + P |
| FR-TKT-008 §4.5 | R/TicketRepository.java:36-37 COUNT PAID; :21 conteo derivado adicional | T/TicketRepositoryTest.java:125-128 cuenta dos PAID excluyendo RESERVED; falta CANCELLED/USED/otro evento/cero | I + P |
| FR-SRC-001 §4.6 | R/EventRepository.java:34-35 DISTINCT JOIN | T/EventRepositoryTest.java:118-150 prueba positiva; falta varios eventos/duplicados | I + P |
| FR-SRC-002 §4.6 | R/EventRepository.java:48-49 venue.city y stageName | T/EventRepositoryTest.java:154-166 solo positivo | I + P |
| FR-SRC-003 §4.6 | R/EventRepository.java:51-62 PUBLISHED, >date, city, LOWER LIKE, DISTINCT, ORDER BY | T/EventRepositoryTest.java:170-183 demuestra búsqueda ignore-case, no toda combinación | I + P |
| FR-SRC-004 §4.6 | R/TicketRepository.java:30-31 >date y ORDER BY asc | T/TicketRepositoryTest.java:139-162 un futuro+un pasado; no orden entre futuros ni frontera | I + P |

## 5. Checklist no funcional — ocho NFR

| ID / PRD §12 | Evidencia / valoración | Estado |
|---|---|---|
| NFR-001 Integridad | V1:11,18,26,38-55,63,76,90-91,105-108,116-147 y V4:47-85 refuerzan datos. Varias restricciones no tienen prueba negativa real; PER-04/08 | I, cobertura incompleta |
| NFR-002 Trazabilidad | M/V1..V4 versionan estructura; V3 añade columna, V4 altera tipo. No evidencia de reescritura reciente de V1–V3; ver historial §9 | I; upgrade NE |
| NFR-003 Reconstrucción vacía | SQL acumulado coherente. T/FlywayMigrationTest.java:23-65 arranca sobre infraestructura efímera, pero no verifica versiones y suite no ejecutada aquí | I + P / NE |
| NFR-004 Portabilidad | T/PostgresContainerSupport.java:14-22 usa PostgreSQLContainer y ServiceConnection; no requiere PostgreSQL instalado manualmente. Sí requiere daemon Docker | I; ejecución NE |
| NFR-005 PostgreSQL real/no H2 | T/PostgresContainerSupport.java:19 imagen postgres:16-alpine; pom.xml:143-146,168-184 dependencias PG/Testcontainers. No dependencia H2 en pom | I; ejecución NE |
| NFR-006 Legibilidad | D/ entidades y R/ ocho interfaces reflejan dominio y separan acceso de negocio. No SQL ni negocio de compra en repositories | I |
| NFR-007 Query Methods/JPQL | R/EventRepository.java:17-62 usa derivados simples y JPQL complejo. R/TicketRepository.java:23-28 usa JPQL simple con FETCH; preferencia académica/justificación PER-10 | I, observación P3 |
| NFR-008 Precisión monetaria | D/Ticket.java:16,39-40 y V1:118 usan BigDecimal / DECIMAL(10,2), no float/double. Falta round-trip de decimales, cero y límite representable | I + F prueba monetaria específica |

## 6. Checklist de aceptación — ocho AC

| ID / PRD §11 | Evidencia existente | Veredicto |
|---|---|---|
| AC-001 VEN-SMR-01 recuperable y capacidad positiva | T/VenueRepositoryTest.java:40-44 guarda capacidad 5000 y comprueba presencia por code; no lee/assert capacidad | I + P |
| AC-002 CMF-2026 y venue recuperados | T/EventRepositoryTest.java:93-99 comprueba isPresent de evento; :57-68 busca por venue con otro código. Falta assert del venue de CMF tras recarga | I + P |
| AC-003 tres artistas y sin duplicar par | T/EventRepositoryTest.java:118-134 usa dos artistas y no repetición. D/Event.java Set + V1:76 protegen la implementación | I + F escenario completo |
| AC-004 segundo perfil rechazado | T/UserProfileTest.java:52-64 usa mismo User con dos perfiles y saveAndFlush esperado | I + T, NE ejecución |
| AC-005 ticketCode duplicado rechazado | T/TicketRepositoryTest.java:166-173 usa TCK-ANDREA-01, no literal TCK-0001; variación equivalente, no error por nombre de fixture | I + T, NE ejecución |
| AC-006 PUBLISHED excluye DRAFT y CANCELLED | T/EventRepositoryTest.java:72-89 excluye DRAFT; no CANCELLED. T/security/DraftVisibilityTest.java:211-217 añade HTTP de DRAFT | I + P |
| AC-007 varios eventos de Solar Beat, una vez cada uno | T/EventRepositoryTest.java:118-134 y :138-150 prueban asociaciones aisladas; no varios eventos de Solar Beat ni unicidad por evento | I + F escenario completo |
| AC-008 contar solo PAID frente PAID/RESERVED/CANCELLED | T/TicketRepositoryTest.java:85-91,125-128 tiene PAID/RESERVED; falta CANCELLED y distractor de otro evento | I + P |

Los valores de ejemplo no son nombres obligatorios de clases/métodos de test. No se exige renombrar pruebas sugeridas del §22 si ya prueban el comportamiento.

## 7. Checklist de calidad — diez QT

| ID / PRD §17 | Evidencia | Estado |
|---|---|---|
| QT-001 Flyway V1–V3 desde vacío | T/PostgresContainerSupport.java:17-22 + T/FlywayMigrationTest.java:23-65. Aplicación usa ahora V4 también. No asserts de history ni ejecución nueva | P / NE |
| QT-002 Hibernate valida sin crear/actualizar | YAML main:28-48 mal anidado; YAML test sin spring.jpa; test de contexto solo assertNotNull | **F, PER-01/02** |
| QT-003 Venue 1:N Event probado | T/EventRepositoryTest.java:56-68 comprueba navegación derivada desde Event; falta recarga inverse y dos eventos para un venue | P |
| QT-004 User 1:1 UserProfile probado | T/UserProfileTest.java:39-64 persistencia+duplicado; lectura de datos/navegación inversa insuficiente | T/P |
| QT-005 Event N:M Artist probado | T/EventRepositoryTest.java:118-150 guarda asociación y consulta por ambos artistas; falta varios eventos/duplicado/recarga | T/P |
| QT-006 Ticket→User/Event probado | T/TicketRepositoryTest.java:95-102 igualdad por código no acredita asociaciones; los JOINs y fixtures aportan evidencia indirecta | P débil |
| QT-007 Query Methods simples/navegación probados | Venue/User/Event/Ticket poseen casos. Algunos métodos nuevos solo se mockean en unit tests y no tienen caso repository dirigido | P |
| QT-008 JPQL JOIN/COUNT probado | T/EventRepositoryTest.java:138-183; T/TicketRepositoryTest.java:106-162; aserciones de discriminación parcial | T/P |
| QT-009 al menos un UNIQUE con saveAndFlush | Venue:53-61, Artist:35-41, Event:103-114, User:78-94, Ticket:166-173, perfil:52-64 | T, **NE ejecución** |
| QT-010 mvn clean test BUILD SUCCESS | Orden explícita de no ejecutar Maven. No se presenta resultado previo como prueba actual | **NE** |

### Inventario contado de pruebas de persistencia

| Archivo fuente dentro de T/ | @Test contados |
|---|---:|
| VenueRepositoryTest.java | 4 |
| ArtistRepositoryTest.java | 3 |
| UserRepositoryTest.java | 9 |
| EventRepositoryTest.java | 8 |
| TicketRepositoryTest.java | 10 |
| UserProfileTest.java | 2 |
| FlywayMigrationTest.java | 3 |
| SecurityCredentialsTest.java | 8 |
| **Total fuente paquete raíz** | **47** |

Uno de los 47 métodos es vacío (V3), por lo que el conteo no debe usarse como prueba de 47 escenarios sustantivos aprobados. El escaneo de todo `src/test/java/com/pulsepass/pass` encontró 264 anotaciones `@Test`; incluye capas fuera de esta auditoría y no equivale al total ejecutado ni contabiliza automáticamente las invocaciones de tests parametrizados. En la lectura directa realizada durante esta auditoría `target/surefire-reports` estaba vacío (cero XML), por lo que no había resultados históricos estables que atribuir. Una búsqueda inicial había enumerado paths de reportes/artefactos; no se usó esa enumeración como ejecución ni como evidencia del código fuente actual.

## 8. Integridad relacional y reglas BR — checklist complementario

| Regla | Evidencia | Valoración |
|---|---|---|
| BR-001 Event exactamente un Venue | D/Event.java:64-66; V1:33-36 NOT NULL FK | I; falta negativo PostgreSQL |
| BR-002 Venue cero/muchos Event | D/Venue.java:47-48,94-96; D/Event.java:64-66; FK no UNIQUE | I; test de un hijo, no cero/dos |
| BR-003 Event/Artist cero/muchos | D/Event.java:68-74; D/Artist.java:41-42; V1:72-82 | I; falta ejercicio completo bidireccional/duplicado |
| BR-004 máximo un perfil | V1:105-108; D/UserProfile.java:39-41 | I + T duplicado |
| BR-005 Ticket un User y Event | V1:121-128; D/Ticket.java:49-55 | I; prueba dirigida débil |
| BR-006 Ticket entidad con datos propios | D/Ticket.java:19-68 | I correcto, no ManyToMany simple |
| BR-007 precisión decimal | D/Ticket.java:39-40; V1:118 | I; sin test decimal round-trip |
| BR-008 enums por nombre | D/Event.java:47-53; D/Ticket.java:35-44; D/UserRole.java:42-44 | I correcto; catálogo textual real no inspeccionado en runtime |
| BR-009 identificadores UNIQUE SQL | V1:11,26,63,90-91,116 | I + tests duplicados; nulls no todos probados |
| BR-010 SOLD_OUT no inferido en MVP persistencia | D/Event.java:140 permite persistir estado; fase servicios §§26–27 añade actualización automática | Evolución permitida, no regresión |

### Esquema acumulado, no lectura aislada de V1

1. **V1** crea venues, events, artists, event_artists, users, user_profiles y tickets. IDs BIGINT identity; event_artists con PK compuesta; constraints de catálogos, precio, capacidad e identificadores de negocio. Índices por FKs/filtros principales.
2. **V2** inserta exclusivamente cinco artistas: Solar Beat, Neon Waves, Caribbean Sound, Ocean Drive, Digital Pulse. No siembra venues, eventos, tickets ni usuarios; no hay colisión operativa de esos identificadores originada por V2. Una colisión entre tests debe analizarse como fixture/aislamiento, no culpar a una semilla inexistente.
3. **V3** añade streaming_url varchar(500) nullable. No es un defecto que no aparezca en V1.
4. **V4** convierte events.event_date a TIMESTAMP, crea user_credentials con user_id PK/FK y password_hash NOT NULL, y user_roles con id PK, UNIQUE(user_id,role), FK y CHECK. No inserta credenciales conocidas para usuarios legados.

El mapping LocalDateTime de Event es coherente con el esquema **final** V4; no reportar incompatibilidad DATE/TIMESTAMP por mirar solo V1. UserProfile.birthDate sigue LocalDate/DATE, correctamente independiente de la fecha/hora del evento.

## 9. Historial: migraciones y pruebas

### Migraciones históricas

- `4c15be3` y `4dade3d` trabajaron un archivo de nombre **`V1_create_schem.sql`** (un solo guion bajo tras V1), con SQL incompleto. Con la convención estándar de Flyway ese nombre no es la migración versionada actual. No asumir que fue ejecutada en una BD.
- `cedf64e`, su reversión `9a73a2f` y `573d81a` introducen/reintroducen los archivos correctamente nombrados V1__create_schema.sql, V2 y V3. `573d81a` elimina el borrador mal nombrado y crea la V1 actual; el diff inspeccionado contiene ese cambio, no una prueba de checksum incompatible en un ambiente real.
- `git diff 573d81a HEAD --` restringido a V1/V2/V3 devolvió vacío: las tres migraciones de nombre válido están iguales en el punto de incorporación estable y el HEAD auditado.
- `4b0dd2d` introduce V4 completa (ALTER de event_date y tablas de seguridad). No se encontró una modificación posterior de ese archivo en el historial de HEAD.

**Conclusión:** no hay fundamento para acusar una reescritura reciente de V1 aplicada por el cambio LocalDate→LocalDateTime: el ALTER está correctamente en V4. Compatibilidad con un entorno que hubiese aplicado otros contenidos sigue **condicional** hasta ver su history/checksum con permiso. Esta auditoría no leyó ni reparó la BD personal. No recomendar `repair` a ciegas, borrar volúmenes ni reescribir scripts aplicados.

### Evolución de pruebas

- `856623b` reconstruyó integración y empleó sufijo IT para varias clases.
- `d2a0271` renombró EventRepositoryIT, FlywayMigrationIT, SecurityCredentialsIT, TicketRepositoryIT y UserProfileIT a `*Test`, además de clases de seguridad. El problema histórico de que Surefire no descubriera `*IT` **ya está corregido para esas fuentes**. No pedir Failsafe ni añadir includes innecesarios para solucionar algo que ya se renombró.
- `8491cef` añade test sobre esquema ya migrado solo en otra rama posterior; no pertenece al HEAD de este informe. Aun allí sus aserciones comprueban reejecución/catálogo, no V3→V4 con datos. Ver PER-03.

## 10. Evolución permitida, contradicciones y decisiones de interpretación

| Tema | Fuentes | Clasificación y decisión de auditoría |
|---|---|---|
| Autenticación/roles fuera de alcance base | Base control/§2.4/§18 frente Seguridad §§1.1,3,9 | Evolución explícita. Credential/Role/V4 no son sobreimplementación prohibida por la fase antigua |
| API REST/capa Service fuera de alcance base | Base §18 frente PRDs de Servicios/Controladores | Evolución aprobada. No pedir eliminar capas |
| SOLD_OUT manual frente automático | Base BR-010 y R-002 frente Servicios §26 | Evolución comercial explícita de fase, no contradicción a corregir en persistencia |
| eventDate DATE→LocalDateTime | V1:31; Servicios §12; D/Event.java:55-56; V4:20-21 | Evolución/alineación hecha mediante migración posterior correcta. Upgrade poblado aún sin prueba |
| Capacidad 5000 frente 3 | Base §16.1=5000; Servicios §47=3; Controladores §23=3; README:305 | Diferencia de fixtures entre capas. Para persistencia el escenario base es 5000. No modificar PRDs ni forzar todas las fixtures a 3 |
| Uno a uno perfil obligatorio en diagrama frente «puede poseer/máximo uno» | Base §6 diagrama `USER ||--|| USER_PROFILE`, FR-USR-003 y BR-004 | Contradicción interna: texto permite cero o uno y FK UNIQUE impone máximo uno. Se adopta requisito explícito máximo uno. No exigir que cada User tenga perfil mediante trigger |
| PK compuesta vs UNIQUE en roles | Seguridad §§9.1/9.2, V4:73-85 | Desviación estructural real con equivalente de unicidad; no ausencia de integridad. PER-07, decisión explícita necesaria si literalidad estricta |
| findByStageName vs IgnoreCase | Servicios §32 ejemplos + permite firmas variables; README:308; R/ArtistRepository.java:13 | No defecto del PRD base: su búsqueda por stageName no obliga ignore-case. Variación ya documentada. No inventar obligación nueva; si se exigiera equivalencia exacta del ejemplo ignore-case, aclarar antes de cambiar comportamiento |
| Arquitectura por funcionalidad de guía frente capas de PulsePass | Guía §§59–61,90; Servicios §§7–8 y Controladores §18 | Guía general de otro ejercicio vs PRD específico que prescribe capas/interfaces. Mantener capas; no exigir reestructuración por funcionalidades ni quitar interfaces de repositories/servicios |
| ErrorResponse «seis» campos en seguridad | Seguridad §3.4 afirma seis, su §13 y Controladores §13 muestran cinco | Contradicción documental ajena a persistencia; no añadir campo a entidad/DDL. Se remite a auditor de seguridad |
| Concurrencia y bloqueo | Base §18/R-001; Servicios §53 | Fuera de alcance explícito. No hallazgo por falta de @Version, locks, cupos atómicos o índices inventados para resolver sobreventa |

## 11. Matriz de consultas y cobertura faltante dirigida

| Repository/método | Evidencia de código | Evidencia de test real / pendiente |
|---|---|---|
| Venue.findByCode | R/VenueRepository.java:13 | T/VenueRepositoryTest.java:40-49 positivos/ausente |
| Venue.findByActiveTrueOrderByNameAsc | R/VenueRepository.java:15 | Se mockea en T/service/impl/VenueServiceImplTest.java:75-85; falta repository dirigido con activo/inactivo y dos nombres. Tests HTTP pueden aportar cobertura indirecta, no equivalen a verificar cada predicado por mock |
| Event.findByEventCode | R/EventRepository.java:17 | T/EventRepositoryTest.java:93-99; sin assert venue/campos |
| Event.existsByEventCode | R/EventRepository.java:19 | Usado por servicios; sin caso repository dedicado verdadero/falso |
| Event.findByEventCodeAndStatus | R/EventRepository.java:28 | T/security/DraftVisibilityTest.java:123-167 aporta HTTP real (publicado/DRAFT, anonimato/roles), no solo mock. Ejecución NE |
| Event.findByStatusOrderByEventDateAsc | R/EventRepository.java:30 | T/EventRepositoryTest.java:72-89 orden positivo; faltan estados excluidos restantes |
| Event.findByVenueCode | R/EventRepository.java:32 | T/EventRepositoryTest.java:57-68; falta venue distractor |
| Event.findByArtistStageName | R/EventRepository.java:34-35 | T/EventRepositoryTest.java:118-150; faltan varios eventos y resultado sin repetición |
| Event.findByArtistStageNameAndStatus | R/EventRepository.java:43-46 | T/security/DraftVisibilityTest.java:175-204 integra filtrado con servicios reales; no queda sin cobertura solo por no estar en EventRepositoryTest |
| Event.findByCityAndArtist | R/EventRepository.java:48-49 | T/EventRepositoryTest.java:154-166 positivo; faltan distractores |
| Event.findRecommendedEvents | R/EventRepository.java:51-62 | T/EventRepositoryTest.java:170-183 ignore-case; faltan múltiples coincidencias/negativos/orden/frontera |
| Artist.findByStageName | R/ArtistRepository.java:13 | T/ArtistRepositoryTest.java:24-31 semilla/existente/ausente |
| Artist.findByActiveTrueOrderByStageNameAsc | R/ArtistRepository.java:15 | Se mockea en T/service/impl/ArtistServiceImplTest.java:91-102; falta prueba repository con inactivo y orden. V2 todos activos no prueba exclusión |
| User.findByUsername / findByEmailIgnoreCase | R/UserRepository.java:12-14 | T/UserRepositoryTest.java:34-50; email en mayúsculas sí probado |
| User.existsByUsername / existsByEmail | R/UserRepository.java:16-18 | T/UserRepositoryTest.java:54-74 verdaderos/falsos |
| User.existsByEmailIgnoreCase | R/UserRepository.java:20 | Sin caso repository propio sobre mayúsculas; lectura case-insensitive de find sí probada |
| UserProfile JpaRepository | R/UserProfileRepository.java:8 | T/UserProfileTest.java:39-64 persiste/duplicado; falta lectura por User/atributos |
| Ticket.findByTicketCode / existsByTicketCode | R/TicketRepository.java:17,39 | T/TicketRepositoryTest.java:95-102,186-194; exists verdadero/falso |
| Ticket.findByUserEmailIgnoreCaseOrderByPurchaseDateDesc | R/TicketRepository.java:19 | Usado por servicio/HTTP; falta test dirigido dos fechas+mayúsculas+distractor. El test JPQL de otro método no lo sustituye |
| Ticket.countByEventEventCodeAndStatus | R/TicketRepository.java:21 | T/service/impl/TicketServiceImplTest.java:97,233,265 lo mockea; falta integración dirigida por estados y evento. El COUNT JPQL hermano no prueba la derivación |
| Ticket.findByUserEmail / AndStatus | R/TicketRepository.java:23-28 | T/TicketRepositoryTest.java:106-121; más negativos pendientes |
| Ticket.findTicketsForFutureEvents | R/TicketRepository.java:30-31 | T/TicketRepositoryTest.java:139-162; falta orden y frontera |
| Ticket.findPaidTicketsByEventCode / countPaidTicketsByEventCode | R/TicketRepository.java:33-37 | T/TicketRepositoryTest.java:125-135; falta CANCELLED/USED/otro evento/cero |
| Credential.findByUserUsername / findByUserEmailIgnoreCase | R/UserCredentialRepository.java:12-14 | T/SecurityCredentialsTest.java:121-128; T/security/LoginServiceTest.java:74-104 y CustomUserDetailsServiceTest.java:57-84 aportan login real |
| Credential.existsByUserUsername / existsByUserEmailIgnoreCase | R/UserCredentialRepository.java:16-18 | T/SecurityCredentialsTest.java:132-138 cubre false username sin credencial; faltan true y email ignore-case |
| Role.findByUserUsername / findByUserEmailIgnoreCase | R/UserRoleRepository.java:13-15 | T/SecurityCredentialsTest.java:88-97 y T/security/CustomUserDetailsServiceTest.java:97-125 username/múltiples/cambio; email no dirigido |
| Role.existsByUserUsernameAndRole / existsByUserEmailIgnoreCaseAndRole | R/UserRoleRepository.java:17-19 | Sin caso repository dedicado. `hasRole` usa findByUserUsername en CustomUserDetailsService.java:100-102, por lo que no prueba estos exists |

No todos los métodos extra de seguridad exigen una clase nueva: basta extender las pruebas existentes donde haya valor. Los escenarios nuevos de API/seguridad deben consolidarse con los otros auditores para evitar contar dos veces el mismo defecto.

## 12. Escenarios de prueba faltantes propuestos, sin implementación

### Obligatorios para cerrar las brechas del PRD

1. **Configuración efectiva:** el contexto de integración expone `hibernate.hbm2ddl.auto=validate` y no update/create; contexto sin ServiceConnection obtiene el datasource configurado desde variables ficticias hacia PG aislado. Acredita PER-01/02 sin leer secretos.
2. **Migraciones fresh:** V1–V4 aplicadas en orden, versión final conocida; catálogo real con todas las tablas/constraints y V3 nullable longitud 500.
3. **Upgrade poblado:** V3 con evento DATE, usuario/perfil/ticket; V4 conserva IDs, relaciones y datos, event_date timestamp a medianoche; usuario legado sigue sin credencial/contraseña conocida. Reejecución final cero migraciones pendientes como caso adicional.
4. **Capacidad:** 0 y negativa rechazadas por `chk_venue_capacity`, positiva aceptada y recuperada por ID/código.
5. **Dinero:** negativo rechazado por `chk_ticket_price`, cero aceptado, valor decimal con centavos recuperado exactamente. Nulabilidad como escenario independiente.
6. **FK obligatorias:** evento sin venue/con FK inexistente; ticket sin usuario/sin evento/con FK inexistente; perfil sin usuario; comprobar rechazo SQL y no solo excepción previa de ORM cuando el objetivo sea integridad PostgreSQL.
7. **Catálogos:** SQL con estado/categoría/tipo/rol no permitidos; casos null separados. Round-trip de todos los enums por nombre textual, no ordinal.
8. **UserProfile:** guardar User+perfil, flush/clear, recuperar User y afirmar nombre, apellido, phone, city, birthDate y único perfil.
9. **Event/Artist:** tres artistas, repetir uno, verificar un solo par en tabla; un mismo artista en dos eventos; flush/clear y navegación en ambos sentidos.
10. **Ticket/User/Event:** recargar ticket y afirmar user.id, event.id y atributos propios; no usar equals por ticketCode como sustituto.
11. **Cartelera:** dos PUBLISHED fuera de orden de inserción y un evento de cada otro estado; solo publicados y orden ascendente.
12. **Búsquedas relacionales:** dos venues/ciudades y varios artistas; excluir explícitamente ciudad/artista erróneos y retornar cada evento una sola vez.
13. **Recomendación:** publicado antes/igual/después de fecha, DRAFT/CANCELLED futuros, otra ciudad, artista parcial con cambio de mayúsculas, dos artistas coincidentes en el mismo evento y dos eventos válidos con orden invertido de inserción.
14. **Tickets futuros:** dos fechas futuras, una igual a corte y una pasada; orden ascendente e IDs exactos.
15. **Ventas:** PAID/RESERVED/CANCELLED/USED, PAID en otro evento, cero ventas; lista y ambos conteos correctos.
16. **Derivadas nuevas:** activos ordenados de Venue/Artist; historial de usuario email ignore-case por purchaseDate desc; exists ignore-case y conteo derivado con estados/distractores.
17. **Registro atómico:** éxito crea las cuatro filas; fallo controlado tardío deja cero filas desde nueva transacción. No basta rollback de la transacción del test.
18. **Aislamiento:** repetir clases de integración en distinto orden contra el contenedor de test, sin depender de restos confirmados; revisar especialmente UserRepositoryTest→TicketRepositoryTest y el count global de artistas.

### Preventivos/condicionados, no requisitos nuevos del MVP

- Eliminar una credencial con usuario/perfil y confirmar la política de preservación, antes de permitir operaciones de gestión de credenciales.
- `equals/hashCode` con proxies/detached cuando se usen objetos de negocio en Sets; no se observó ejecución que pruebe defecto actual.
- Límite representable de NUMERIC(10,2), escala superior y política de redondeo solo si se desea concretar un contrato monetario que el PRD no detalla.
- Medición de índices/N+1, sin exigir paginación, locks, @Version ni diseño de inventario concurrente.

## 13. Guía y restricciones técnicas

- Separación de responsabilidades (guía §§55–58): repositories contienen acceso a datos, no cálculo de edad/precio/capacidad ni política HTTP. Conforme por inspección.
- Paquete raíz/componentes (guía §40): `com.pulsepass.pass` contiene domain/repository como subpaquetes; interfaces Spring Data registradas sin instanciación manual en producción.
- Inyección (guía §§22,85–86): `@Autowired` en tests no constituye incumplimiento de la recomendación de constructor injection para clases de aplicación. Los repositories son interfaces generadas por Spring Data; no requieren implementaciones escritas a mano.
- Maven/Java/Boot: `pom.xml:8` Boot 4.1.1; :30 Java 21; :49-62 JPA/Flyway/PG; :143-146 driver; :168-184 Testcontainers. No se ejecutó resolución de dependencias ni compilación. No afirmar error de dependencia por recordar nombres de otra versión.
- `@Data`/Lombok: no hay `@Data` en las entidades leídas; que pom incluya Lombok no viola prohibición de `@Data` sobre entidades.
- Consultas: no hay `nativeQuery=true` ni SQL nativo funcional en los repositories leídos. COUNT y JOIN son JPQL.
- Configuración externa: la estructura main declara variables DB pero en namespace incorrecto; PER-01. No se auditó ni pidió el valor real de ninguna variable.
- Organización por funcionalidad de la guía no justifica refactorizar el árbol específico por capas de PulsePass. El mínimo cambio completo no incluye reorganización del proyecto.

## 14. Plan mínimo de corrección recomendado — no ejecutado

1. **P1:** corregir exclusivamente el anidado de configuración; fijar y probar validate en integración. Esto es prerequisite para atribuir valor a los tests de esquema.
2. **P2:** reemplazar test vacío de V3, añadir prueba de upgrade real V3→V4 y catálogo; no modificar V1–V4.
3. **P2:** ampliar casos existentes para capacidad/precio/FK/catalogos y relaciones; reforzar aserciones sin equals por código.
4. **P2:** cerrar AC-003/007 y filtros/orden/COUNT; eliminar dependencia del orden de tests con rollback/fixtures propias.
5. **P2/decisión:** aceptar expresamente la equivalencia de PK/UNIQUE de roles o planificar migración posterior y clave JPA compuesta. No cambiar una migración histórica por una preferencia estructural.
6. **P2 con seguridad:** probar atomicidad real del registro y la política de cascada de credencial; no duplicar el informe de la otra capa.
7. **P3 opcional:** documentación precisa de JOIN FETCH e índice potencialmente redundante; no priorizar microoptimización sobre cobertura obligatoria.

## 15. Límites y verificaciones de esta auditoría

- **Solo lectura del repositorio.** Se usaron read_file, inventario, búsquedas y comandos git read-only. El único entregable escrito es este archivo scratch, fuera del repositorio.
- **No Maven**, ni pruebas nuevas, ni arranque de aplicación, ni conexiones a PostgreSQL del usuario; ningún `clean`, `migrate`, `repair`, reset, commit o cambio de rama.
- Se verificaron counts de requisitos y anotaciones de prueba programáticamente. El primer intento de buscar ciertos archivos con search_files devolvió error de shell; se recuperó inventario con pathlib y git. PyYAML no estaba disponible; el análisis de namespaces se corroboró mediante lectura numerada y un probe estándar de indentación que imprime solo nombres de claves, sin valores secretos. No se instaló nada.
- Ningún hallazgo de cobertura se presenta como test fallido observado; ningún test presente se declara aprobado. Los reportes de la ejecución padre deben añadirse a la consolidación por separado, con comando y resultados reales.
- No se auditó la history/checksum de una BD aplicada ni se certificó compatibilidad de upgrade con un despliegue existente; riesgo condicionado, no defecto de checksum demostrado.
- Fuera del alcance: evaluación exhaustiva de servicios/autorización/JWT, frontend, despliegue productivo, rendimiento medido y concurrencia explícitamente excluida por el PRD de Servicios §53.

**Cierre:** implementación funcional de persistencia mayormente presente por lectura; cumplimiento integral aún no demostrado. El bloqueo principal es configuración efectiva, seguido por evidencia insuficiente de integridad, migraciones evolutivas y escenarios de aceptación. No se modificó código para «hacer pasar» pruebas ni se sustituyeron resultados reales por salidas inventadas.


---

# Anexo B — Servicios: reglas, DTOs y tests mínimos

# Auditoría de servicios de PulsePass

## Veredicto

**POR CORREGIR.** Las reglas funcionales de la fase de servicios están implementadas por lectura, pero la autorización de la fase posterior concede privilegios no autorizados a STAFF y no exige USER para comprar. Faltan pruebas unitarias obligatorias de asociación de artistas y de la estrategia de precios; varias pruebas mínimas presentes solo comprueban DTOs simulados, no la mutación real. **CRÍTICO:** no se ha demostrado un bloqueo de compilación ni una incidencia P0. Hay un riesgo de autorización P1 sustentado en código, pendiente de reproducción por la ejecución del auditor principal.

**COMPLETO por inspección, no por ejecución:** cinco interfaces e implementaciones originales, DTOs record, cinco mappers MapStruct, excepciones semánticas, inyección por constructor y fronteras transaccionales de las escrituras. No se ha ejecutado Maven, Java, tests ni la aplicación en esta auditoría.

## Base y alcance

- Repositorio: `C:/Users/57321/Desktop/pulse_pass`.
- Rama: `feature/rebuild-pulsepass`; HEAD: `5c9056f385f0f459f39b809029718db2b8f0ee98`.
- `git status --short`: sin cambios versionados; preexistentes no versionados `EstadoSesion_Capa_Seguridad.md`, `MatrizAutorizacion.md`, `PlanAccion_Capa_Seguridad.md`, `error`, `guia/`. Sin conflictos sin resolver. La comprobación final conserva ese estado.
- Fuentes leídas completas: `PRD_PulsePass_Capa_Servicios.pdf` (34 páginas), `guia/capa service.md` (3093 líneas), `PRD_PulsePass_Capa_Seguridad_Spring_Security_JJWT.pdf` (26 páginas) y `PRD_PulsePass.md` (495 líneas).
- Leídos completos: todas las interfaces y clases bajo `service/`, todos los DTOs y mappers, y los siete archivos `src/test/java/com/pulsepass/pass/service/impl/*Test.java`. Trazados a entidades, repositories, POM, handlers, configuración de rutas y pruebas de seguridad/controladores pertinentes.
- `read_file` falló por su backend de shell; se usaron lecturas UTF-8 con Python mediante `terminal`. Para PDFs se utilizó `pypdf` vía `uv run --with pypdf`, sin instalar dependencias en el repositorio. Extracciones en scratch. No se leyó `.env`.
- Los resultados existentes de `target/` no se consideran ejecución propia. Se inspeccionaron los generados existentes de EventMapper/TicketMapper únicamente como evidencia histórica auxiliar. La ejecución y consolidación de tests corresponde al auditor principal.

### Convenciones

Rutas abreviadas en tablas:

- `M/` = `src/main/java/com/pulsepass/pass/`.
- `T/` = `src/test/java/com/pulsepass/pass/`.
- `S` = PRD de Servicios; `SEC` = PRD de Seguridad; `G` = guía de servicios.
- **OK-L**: comportamiento localizado por lectura; **P**: prueba ausente o insuficiente; **D**: divergencia literal de contrato; **E**: evolución intencional; **NV**: no verificado por ejecución. Ningún OK-L equivale a test aprobado.
- P1 alta: autorización incorrecta con acceso/mutación indebidos; P2 media: incumplimiento arquitectónico o de pruebas obligatorias; P3 baja: contrato Java literal sin rotura interna observada.

## Hallazgos verificables y corrección mínima

### H1 — P1 — STAFF puede consultar datos/listados ajenos y cancelar tickets ajenos

- **Obligación:** SEC §11 USR-02/USR-03 y TKT-03 permiten propietario o ADMIN; TKT-05 permite propietario USER o ADMIN autorizado. SEC §11.1 permite STAFF global exclusivamente para consulta individual de ticket y uso, no para todas las rutas con ownership.
- **Evidencia:** `M/service/impl/AuthorizationServiceImpl.java:58-73` permite STAFF o ADMIN indistintamente en `requireOwnerOrPrivileged`. Se reutiliza en `M/service/impl/UserServiceImpl.java:81,91`, `M/service/impl/TicketServiceImpl.java:164,191`. `M/security/SecurityConfig.java:122-131` solo exige autenticación a esas rutas; no existe otra barrera de rol que cierre el acceso. `M/controller/TicketController.java:41-48` y `M/controller/UserController.java:45-52` delegan directamente.
- **Resultado deducible:** un principal STAFF activo, distinto del dueño, pasa el helper y obtiene datos del usuario, lista de tickets y, si el ticket está PAID y futuro, lo cancela. La cancelación produce escritura; no es solo una posibilidad de acceso directo interno.
- **Impacto:** divulgación de datos y modificación no autorizada de recursos de terceros. No se ha hecho una petición HTTP de reproducción en este subanálisis.
- **Corrección mínima:** conservar el helper STAFF/ADMIN para TKT-02; en USR-02/03, TKT-03 y TKT-05 exigir dueño o ADMIN, sin bypass de STAFF. El ajuste debe vivir en la frontera de servicio, no únicamente en el Controller. No añadir asignaciones de personal a eventos: SEC las difiere.
- **Pruebas:** STAFF distinto del dueño debe recibir 403 en las cuatro operaciones; en cancelación comprobar estado PAID sin cambios y ninguna persistencia. El test actual `T/service/impl/AuthorizationServiceImplTest.java:112-117` prueba el permiso genérico de STAFF, pero no que solo se use en la operación adecuada. `T/security/OwnershipAntiIdorTest.java:212-304` prueba terceros USER y ADMIN, no estos casos STAFF.

### H2 — P1 — La compra comprueba identidad, pero omite el rol USER obligatorio

- **Obligación:** SEC §11 TKT-01: `USER; userEmail debe pertenecer al principal`; SEC §11 aclara que STAFF/ADMIN no son propietarios automáticos de todas las rutas de autoservicio.
- **Evidencia:** `M/service/impl/TicketServiceImpl.java:58-68` solo llama `requireSelf(user.getId())`; `M/service/impl/AuthorizationServiceImpl.java:89-99` verifica activo e ID, no ROLE_USER. `M/security/SecurityConfig.java:117-131` no tiene matcher de POST `/api/tickets` con `hasRole("USER")`; cae en `authenticated()`.
- **Resultado:** STAFF o ADMIN sin USER (y una cuenta autenticable sin roles) puede comprar para sí mismo si satisface las reglas de negocio. No se presume jerarquía de roles: no se declaró una que convierta STAFF/ADMIN en USER.
- **Corrección mínima:** exigir USER en la ruta de compra y conservar `requireSelf`; si se desea una frontera de servicio íntegra también para llamadas internas, exigir ese rol antes de comprar allí. Añadir casos STAFF-only, ADMIN-only y sin roles, además de USER propio/ajeno. El test `T/security/AuthorizationMatrixTest.java:240-251` solo distingue anónimo y USER; por eso no detecta la omisión.

### H3 — P2 — AuthService reemplaza el registro de negocio en lugar de orquestar UserService

- **Obligación arquitectónica:** SEC §3.2, §7.1 y §9.3: preservar UserService y sus reglas; AuthService orquesta usuario/hash en una transacción y no se convierte en sustituto de UserService.
- **Evidencia:** `M/service/impl/AuthServiceImpl.java:64-88` no recibe UserService; `:131-177` replica íntegramente unicidad, birthDate, creación de User y UserProfile, mientras `M/service/impl/UserServiceImpl.java:39-72` mantiene la implementación anterior. `M/controller/UserController.java:39-42` invoca solo AuthService. Una búsqueda de `userService.register` en producción no encontró invocaciones.
- **Impacto:** las reglas actuales coinciden funcionalmente, pero el registro HTTP deja de usar el servicio dueño del caso de uso; una corrección en UserService no afecta al registro público. El comentario de `M/service/AuthService.java:14-16` dice que se invoca, contradicho por la implementación. No es un fallo de atomicidad por sí mismo: AuthService sí tiene `@Transactional`.
- **Corrección mínima:** delegar las reglas/creación a `UserService.register` dentro de la transacción exterior de AuthService y resolver el User persistido desde el DTO retornado para crear credencial/rol. Mantener el contrato de salida y probar que una regla del servicio delegado impide todas las escrituras de credencial/rol. No hacen falta un nuevo servicio genérico ni una refactorización transversal.

### H4 — P2 — Faltan unit tests obligatorios de addArtist

- **Obligación:** S §49, fila FR-SVC-007: `Unit test requerido`, BR-EVENT-010/011; S §42 exige servicio real con Repository/Mapper mock.
- **Evidencia:** `T/service/impl/EventServiceImplTest.java:57-234` contiene exactamente ocho tests de find/create/publish y ninguna invocación a `eventService.addArtist`. Código productivo en `M/service/impl/EventServiceImpl.java:149-166`.
- **Matiz:** sí existe cobertura HTTP real positiva de asociación en `T/controller/EventControllerTest.java:281-291`, no un unit test ni cobertura de los rechazos BR-EVENT-010/011. No debe afirmarse que addArtist carece absolutamente de toda prueba.
- **Impacto:** duplicados, CANCELLED/FINISHED y recursos inexistentes no tienen regresión unitaria demostrada.
- **Corrección mínima:** añadir pruebas de éxito con asociación real y guardado; duplicado; CANCELLED y FINISHED; evento/artista inexistentes, con `never().save` y mapper no invocado en rechazo.

### H5 — P2 — La estrategia real de precios no está probada

- **Obligación:** S §28: estrategia encapsulada **y probada**, BigDecimal y precio no negativo.
- **Evidencia:** `M/service/PriceCalculator.java:23-36` define base 100000 y multiplicadores GENERAL 1, STUDENT 0.80, VIP 2.50, BACKSTAGE 4.00. `T/service/impl/TicketServiceImplTest.java:53-54,98,268` mockea PriceCalculator; los únicos stubs cubren VIP/GENERAL. La búsqueda en todos los tests no encontró un test de `calculatePrice` real ni aserciones de resultado `price`/`getPrice`/JSON `$.price`.
- **Matiz:** los tests HTTP de compra utilizan el componente real, pero `T/controller/TicketControllerTest.java:118-122` solo comprueba status/type/userEmail/eventCode. Ejecutar un cálculo sin afirmar su valor no prueba su política.
- **Impacto:** cambiar descuento o multiplicadores incorrectamente no tiene una aserción que lo detecte.
- **Corrección mínima:** un test puro, preferiblemente parametrizado, que ejecute el PriceCalculator real para los cuatro TicketType y compare resultados mediante `isEqualByComparingTo`, incluyendo no negatividad. No se requiere PricingService nuevo ni una estrategia intercambiable.

### H6 — P2 — Varias pruebas de transición pueden pasar aunque no se muten las entidades

- **Obligación:** S §43 TEST-EVENT-007, §45 TEST-TICKET-001/009/011; G sesión 13 (`:2399-2423`): probar comportamiento, no solo resultado simulado.
- **Evidencia y fallo de la prueba:**
  - `T/service/impl/EventServiceImplTest.java:197-214`: mapper devuelve un DTO preconstruido PUBLISHED y solo se afirma `result.status()`. Quitar `setStatus(PUBLISHED)` del servicio no rompe esa aserción.
  - `T/service/impl/TicketServiceImplTest.java:87-107`: `save(any())` devuelve un Ticket PAID preconstruido, independiente del creado por el servicio. No se captura el estado/tipo/precio/relaciones del argumento realmente enviado a save.
  - `T/service/impl/TicketServiceImplTest.java:292-309,343-363`: cancelar/usar afirma status del DTO mockeado y `save(any())`, no estado real del Ticket. Quitar las respectivas mutaciones no invalida esos unit tests.
  - `T/service/impl/EventServiceImplTest.java:105-126` también usa un retorno de save/mapper prefabricado: cumple el mínimo TEST-EVENT-003 (save invocado), pero no demuestra el estado inicial DRAFT del objeto creado (AC-001).
- **Matiz:** los tests HTTP reales sí aportan cobertura de publicación, compra PAID, cancelación propia y uso; `T/controller/EventControllerTest.java:235-244`, `T/controller/TicketControllerTest.java:109-122`, `T/security/OwnershipAntiIdorTest.java:254-263,322-333`. Eso no vuelve adecuadas las aserciones de los unit tests mínimos.
- **Corrección mínima:** aserción sobre `event.getStatus()` para publish, captor/`argThat` sobre el Ticket enviado a save en purchase, y aserción sobre la entidad mutada en cancel/use. Probar DRAFT sobre el argumento real de create. Mantener la comprobación DTO y mapper como verificación separada.

### H7 — P2 — Atomicidad de registro declarada, pero no probada con rollback real

- **Obligación:** SEC §9.3 y §17.3 requieren transacción atómica de registro probada selectivamente con PostgreSQL; S §20 BR-USER-004 y §39 explican la atomicidad correspondiente.
- **Evidencia:** `T/service/impl/AuthServiceImplTest.java:162-196` se llama `register_createsUserCredentialAndRoleAtomically`, pero se ejecuta con Mockito (`:57-69`), sin proxy transaccional, y solo comprueba hash y rol. No induce fallo ni demuestra rollback de User/UserProfile. La búsqueda de rollback y de invocaciones de registro en tests no encontró una prueba de integración con fallo inducido del registro y relectura posterior. `T/SecurityCredentialsTest.java:49-139` verifica persistencia/constraints de credenciales/roles de forma separada, no el rollback de AuthService.register.
- **Impacto:** `@Transactional` está correctamente declarada por lectura, pero no hay evidencia de aceptación que detecte un registro parcialmente confirmado si se rompe la frontera transaccional.
- **Corrección mínima:** integración aislada que provoque fallo después de crear usuario/perfil (en credencial o rol), invoque AuthService a través de su proxy y, después de finalizar esa transacción, verifique que no quedaron las filas. Evitar que la propia transacción del test o su rollback automático sea el único motivo de desaparición. No introducir PostgreSQL en los unit tests de servicios.

### H8 — P3 — Nombre literal del contrato mínimo TicketService distinto del PRD

- **Obligación exacta:** S §21 declara `findPaidTicketsByEvent(String eventCode)`.
- **Evidencia:** `M/service/TicketService.java:16` y `M/service/impl/TicketServiceImpl.java:173` exponen `findPaidTicketByEvent` (Ticket singular). `M/controller/EventController.java:70-72` ya usa el nombre implementado, por lo que no se observa rotura interna del HTTP.
- **Impacto:** un consumidor Java escrito contra la interfaz mínima documentada no compila; el comportamiento de consulta sí cumple. La flexibilidad de firmas de S §32 se refiere a los ejemplos de **Repositories**, no concede expresamente renombrar el contrato Service de §21.
- **Corrección mínima:** alinear el nombre en interfaz, implementación y caller. Si el docente acepta la variante funcional, registrar esa aceptación y no tratarla como fallo de negocio. No requiere cambiar la ruta HTTP.

## Checklist de contratos y requisitos estructurales

| Requisito | Estado por lectura | Evidencia / interpretación |
|---|---|---|
| SRV-001 / S §7 no entidades en salida | OK-L | Las cinco interfaces originales retornan DTOs/List<DTO>; no Event/User/Ticket/Venue/Artist. |
| SRV-002 / NFR-002 constructor + final | OK-L | Los cinco Impl originales y AuthServiceImpl reciben dependencias final por constructor. AuthorizationService no tiene dependencias persistentes. |
| SRV-003 interfaces/implementaciones | OK-L | Venue, Artist, Event, User, Ticket existen en `M/service/` y `impl/`. |
| SRV-004 / S §38 transacciones | OK-L / NV runtime | Clases originales readOnly y escrituras con override @Transactional; tabla detallada más abajo. |
| SRV-005 / NFR-006 separación de reglas | OK-L con H3 | SQL no aparece en Services; consultas en repositories, decisiones en servicios. Duplicación Auth/User sí rompe la responsabilidad del registro. |
| S §10 VenueService | OK-L | Firmas `findByCode`, `findActiveVenues` exactas. |
| S §11 EventService | OK-L / E | Seis firmas exactas. Visibilidad de findByCode/findByArtist restringida intencionalmente por SEC §11. |
| S §17 ArtistService | OK-L | Tres firmas exactas. |
| S §18 UserService | OK-L / E | Tres firmas exactas; autorización añadida. Ruta de registro ahora AuthService por SEC §9.3, admisible; duplicación del negocio no lo es (H3). |
| S §21 TicketService | D | Cinco nombres exactos y uno singular en vez de plural; H8. |
| S §32 repositories necesarios | OK-L funcional | Todos los recorridos y consultas de servicios tienen métodos existentes. Duplicados usan Optional de find en vez de exists: variación funcional válida. |
| S §35 excepciones | OK-L | ResourceNotFoundException, DuplicateResourceException, BusinessRuleException extienden RuntimeException y reciben String; mensajes identifican recurso/regla. No se exige BusinessException base de CourseHub. |
| S §36 Optional | OK-L | Se usa orElseThrow/map/isPresent; no Optional.get() desprotegido en servicios. |
| S §37 funcional | OK-L | stream.map(...).toList en listados; guardas imperativas claras en negocio. No hay obligación de convertir todas las guardas a streams. |
| NFR-001 / S §42 aislamiento | OK-L / NV ejecución | Los siete tests service/impl usan MockitoExtension, sin SpringBootTest, base de datos ni Testcontainers. Las integraciones de seguridad adicionales no invalidan ese aislamiento. |
| NFR-003 DTOs inmutables | OK-L con matiz | Todos son record. Las colecciones de DTOs no tienen copia defensiva: inmutabilidad superficial, hardening opcional. |
| NFR-004 MapStruct | OK-L / NV compilación nueva | Cinco @Mapper(componentModel="spring"); rutas de propiedades verificadas. |
| NFR-005 errores significativos | OK-L | Mensajes contienen códigos/identidad/regla; errores auth/denegación son genéricos deliberadamente. |
| S §8 estructura | OK-L | Estructura de capas y `service/impl`; §8 dice sugerida. El árbol por feature de CourseHub en G §15 es recomendación, no mandato para reorganizar PulsePass. |
| S §4.2 exclusiones | Respetadas por fase / E | Security y Controllers se añadieron en fases posteriores explícitas. No son incumplimiento de esta fase. |
| S §50 mvn clean test | NV | Prohibido ejecutar Maven en este encargo. No se infiere verde a partir de archivos target. |

### DTOs: forma, tipos y exposición

| DTO | Evidencia | Veredicto |
|---|---|---|
| CreateEventRequest | `M/dto/request/CreateEventRequest.java:10-31` | S §12: siete campos y LocalDateTime; mínimo de edad int frente a Integer del **ejemplo**, no una obligación literal inequívoca. Rechaza negativo mediante Service y @Min. Estado no controlable por request. |
| RegisterUserRequest | `M/dto/request/RegisterUserRequest.java:9-48` | S §19: siete campos originales; password adicional requerido por SEC §3.4/9.3. @Size(12..72 caracteres), @NotBlank; AuthService impone máximo 72 bytes UTF-8. Sin rol aceptado del cliente. |
| PurchaseTicketRequest | `M/dto/request/PurchaseTicketRequest.java:8-18` | S §22: email/eventCode/type, sin precio confiado al cliente. Identidad contrastada por ID; rol insuficiente H2. |
| EventResponse | `M/dto/response/EventResponse.java:8-20` | S §13: incluye los once campos mínimos; artistas son Set<ArtistResponse>, no entidades. minimumAge int convierte null del dominio a 0 en generado histórico. |
| TicketResponse | `M/dto/response/TicketResponse.java:8-18` | S §23: nueve campos exactos, BigDecimal y LocalDateTime; sin User/Event JPA. |
| VenueResponse | `M/dto/response/VenueResponse.java:3-11` | Datos escalares con Long capacity y Boolean active, compatibles con entidad. |
| ArtistResponse | `M/dto/response/ArtistResponse.java:3-9` | Escalares completos, sin relación Event. |
| EventSummaryResponse | `M/dto/response/EventSummaryResponse.java:7-14` | Escalares para listado; S no fija forma exacta adicional. |
| UserResponse | `M/dto/response/UserResponse.java:3-8` | ID/username/email/active. S no exige perfil embebido; SEC exige conservarlo sin password/hash. |
| LoginRequest/AuthResponse/AuthenticatedUserResponse | Archivos respectivos `:12-19`, `:16-20`, `:16-22` | Records de evolución SEC §10.1; sin hash/contraseña en respuesta. AuthenticatedUserResponse contiene roles como strings. |
| ErrorResponse | `M/dto/response/ErrorResponse.java:6-12` | Cinco componentes reales. SEC §3.4 dice "seis campos" pero SEC §13 muestra exactamente estos cinco: contradicción documental, no añadir campo inventado. |

### MapStruct y build

- `M/mapper/VenueMapper.java:7-10`, `ArtistMapper.java:7-10`, `UserMapper.java:7-10`: mapeo por nombre compatible con los getters reales.
- `M/mapper/EventMapper.java:9-22`: venue.code→venueCode y venue.name→venueName existen en Event.getVenue (`M/domain/Event.java:132`) y Venue.getCode/getName (`M/domain/Venue.java:66,70`). `artists` se transforma estructuralmente en ArtistResponse; no es obligatorio declarar `uses=ArtistMapper` cuando MapStruct genera el mapeo anidado. El generado histórico `target/generated-sources/annotations/com/pulsepass/pass/mapper/EventMapperImpl.java:117-149` lo muestra, sin exponer entidades.
- `M/mapper/TicketMapper.java:11-14`: user.email, event.eventCode y event.name existen (`M/domain/Ticket.java:95-104`, `User.java:64`, `Event.java:100-105`). El generado histórico accede a esas rutas, no depende de nombres imaginados.
- No mapper de request necesario: construir la entidad en Service y fijar DRAFT/PAID no viola el PRD; evita que mapper ejecute reglas.
- `pom.xml:30` Java 21; `:8` Boot 4.1.1; `:65-69` starter-test; `:149-159` MapStruct 1.5.5.Final y processor provided. Processor disponible por classpath en Java 21; no se exige necesariamente annotationProcessorPaths explícito. Los generados existentes son evidencia histórica, no prueba de que esta sesión compiló.
- Lombok es opcional en POM (`:162-166`); constructor explícito cumple la guía (`G:443-453`) sin @RequiredArgsConstructor. No se exige introducir Lombok ni interfaces Strategy/Factory en todos los casos.
- No tests unitarios específicos de mappers encontrados; útil como mejora, pero S no los enumera entre mínimos obligatorios. No confundir mocks de mappers con haber probado MapStruct.

## Checklist completo de requisitos funcionales S §40

| ID | Caso de uso | Evidencia implementación | Prueba observada (no ejecutada) |
|---|---|---|---|
| FR-SVC-001 | Venue por código | `M/service/impl/VenueServiceImpl.java:25-28` | VenueServiceImplTest:34/55. |
| FR-SVC-002 | Venues activos | mismo `:32-36` | VenueServiceImplTest:68. |
| FR-SVC-003 | Crear evento | EventServiceImpl:47-80 | EventServiceImplTest:97/130/149/171; DRAFT real no capturado en unitario. |
| FR-SVC-004 | Evento por código | EventServiceImpl:84-102 | EventServiceImplTest:58/84; DraftVisibilityTest:124-167 para evolución segura. |
| FR-SVC-005 | Publicados | EventServiceImpl:117-121 | Sin unit test de este método; HTTP EventControllerTest:199-210 y DraftVisibilityTest:212-217. |
| FR-SVC-006 | Publicar | EventServiceImpl:126-144 | EventServiceImplTest:193/218, con debilidad H6; HTTP real positivo existente. |
| FR-SVC-007 | Asociar artista | EventServiceImpl:149-166 | Falta unit test requerido H4; HTTP positivo EventControllerTest:281-291. |
| FR-SVC-008 | Eventos por artista | EventServiceImpl:170-181 | Sin unit test; HTTP DraftVisibilityTest:175-204 cubre filtrado. |
| FR-SVC-009 | Consultar artista | ArtistServiceImpl:25-43 | ArtistServiceImplTest:34/53/66/84; falta lookup stageName inexistente unitario. |
| FR-SVC-010 | Registrar usuario/perfil | UserServiceImpl:39-72; AuthServiceImpl:137-177 | UserServiceImplTest:49/79/97/118; AuthServiceImplTest:162-289. Duplicación H3 y rollback H7. |
| FR-SVC-011 | Usuario por email | UserServiceImpl:75-83 | Sin unit test propio; HTTP OwnershipAntiIdorTest:271-303. H1 para STAFF. |
| FR-SVC-012 | Usuario por username | UserServiceImpl:86-93 | Sin unit test propio; HTTP OwnershipAntiIdorTest:281-284 y AuthorizationMatrixTest:228-232. H1. |
| FR-SVC-013 | Comprar | TicketServiceImpl:56-136 | TicketServiceImplTest:70-280; H2/H5/H6. |
| FR-SVC-014 | Ticket por código | TicketServiceImpl:140-150 | Sin unit test propio; HTTP OwnershipAntiIdorTest:171-205. |
| FR-SVC-015 | Tickets de usuario | TicketServiceImpl:154-169 | Sin unit test propio; HTTP OwnershipAntiIdorTest:213-231. H1. |
| FR-SVC-016 | PAID por evento | TicketServiceImpl:173-177; TicketRepository:33-34 | Sin unit test Service; TicketRepositoryTest:134 y AuthorizationMatrixTest:287-297 aportan evidencia de otras capas. H8 nombre. |
| FR-SVC-017 | Cancelar | TicketServiceImpl:182-206 | TicketServiceImplTest:284/313 y HTTP propia/tercero; H1/H6. |
| FR-SVC-018 | Marcar usado | TicketServiceImpl:211-239 | TicketServiceImplTest:335/367; HTTP STAFF/USER; H6. |

Todas las rutas funcionales anteriores están implementadas por lectura; ese hecho no aprueba automáticamente sus restricciones de seguridad ni su cobertura.

## Checklist de todas las reglas de negocio de S

| Regla | Estado funcional por lectura | Evidencia y cobertura |
|---|---|---|
| BR-VENUE-001 | OK-L | VenueServiceImpl:26-28; test inexistente :55-64. |
| BR-VENUE-002 | OK-L | VenueServiceImpl:33 usa query active=true; test :68-85. Filtrado real lo hace Repository, apropiado. |
| BR-EVENT-001 código único | OK-L / P | EventServiceImpl:48-50; sin unit test de código duplicado (import DuplicateResourceException del test no se usa). |
| BR-EVENT-002 venue existe | OK-L | EventServiceImpl:52; test :130-145, never save. |
| BR-EVENT-003 venue activo | OK-L | EventServiceImpl:54-56; test :149-167. |
| BR-EVENT-004 fecha futura creación | OK-L | EventServiceImpl:58-60; test :171-189 con fecha pasada. Borde igualdad no cubierto. |
| BR-EVENT-005 DRAFT inicial | OK-L / P parcial | EventServiceImpl:66-75 fija DRAFT; unit create usa retorno prefabricado (H6). |
| BR-EVENT-006 minimumAge >=0 | OK-L / P | EventServiceImpl:62-64, DTO @Min; sin prueba unitaria de rechazo negativo. |
| BR-EVENT-007 solo DRAFT publicable | OK-L / P parcial | EventServiceImpl:130-132; prueba CANCELLED y éxito con DTO débil. No unit tests de PUBLISHED/SOLD_OUT/FINISHED. HTTP PUBLISHED rechazo en EventControllerTest:261-277. |
| BR-EVENT-008 futuro al publicar | OK-L / P | EventServiceImpl:134-136; sin unit test específico de evento DRAFT vencido. |
| BR-EVENT-009 venue sigue activo | OK-L / P | EventServiceImpl:138-140; sin unit test de publicación en venue inactivo. |
| BR-EVENT-010 artista no duplicado | OK-L / P obligatoria | EventServiceImpl:158-160; contains se apoya en equals por stageName (`M/domain/Artist.java:86-95`); H4. |
| BR-EVENT-011 no agregar a CANCELLED/FINISHED | OK-L / P obligatoria | EventServiceImpl:154-156; H4. |
| BR-ARTIST-001 inexistente | OK-L / P parcial | ArtistServiceImpl:25-35; test inexistente por ID, no por stageName. |
| BR-ARTIST-002 activos | OK-L | ArtistServiceImpl:40; ArtistServiceImplTest:84-102. |
| BR-USER-001 username único | OK-L | UserServiceImpl:41-43 y AuthServiceImpl:140-143; duplicado cubierto en ambas clases. |
| BR-USER-002 email único ignorecase | OK-L / P parcial | UserServiceImpl:45-47 y AuthServiceImpl:145-148 usan findByEmailIgnoreCase. Tests duplicados con mismo case; no caso mixto en unitario. No se exige normalización del string para satisfacer búsqueda ignorecase. |
| BR-USER-003 activo inicial | OK-L / P parcial | UserServiceImpl:53-57 y AuthServiceImpl:155-156 fijan true; los tests no capturan el User real original. |
| BR-USER-004 User+Profile transacción | OK-L / NV rollback | UserServiceImpl:38,68-70; AuthServiceImpl:136,155-175. Una transacción por operación; H7 para aceptación de seguridad. |
| BR-USER-005 birthDate no futura | OK-L | UserServiceImpl:49-51 y AuthServiceImpl:150-153; tests futuros presentes. |
| BR-TICKET-001 usuario existente | OK-L | TicketServiceImpl:58-59; test :111-123. |
| BR-TICKET-002 usuario activo | OK-L | TicketServiceImpl:70-73; test :127-141. Filtro de seguridad además bloquea cuentas inactivas HTTP; evolución válida. |
| BR-TICKET-003 evento existente | OK-L / P | TicketServiceImpl:76-77; sin test unitario de compra con evento inexistente. |
| BR-TICKET-004 solo PUBLISHED | OK-L / P parcial | TicketServiceImpl:80-83; unit DRAFT y CANCELLED, faltan SOLD_OUT y FINISHED. |
| BR-TICKET-005 no evento ocurrido | OK-L / P | TicketServiceImpl:86-88; sin unit test de compra con PUBLISHED pasado. |
| BR-TICKET-006 edad fecha evento | OK-L / P parcial | TicketServiceImpl:91-101 usa Period entre birthDate y eventDate.toLocalDate; rechaza perfil/fecha faltantes si restricción >0. Test menor usa fecha fija 2010 y evento dependiente de now, no demuestra frontera cumpleaños ni evaluación en fecha del evento. |
| BR-TICKET-007 capacidad | OK-L | TicketServiceImpl:105-109 cuenta PAID y rechaza >=capacity; test :219-239. No cuenta USED, acorde a regla explícita. |
| BR-TICKET-008 último cupo SOLD_OUT atómico | OK-L / NV rollback | TicketServiceImpl:126-134 en misma @Transactional; test :243-280 secuencia de conteos y entidad SOLD_OUT. No se ha probado rollback real de compra; S excluye integración Service. |
| BR-TICKET-009 precio no negativo | OK-L / P obligatoria pricing | PriceCalculator:23-36 solo constantes/multiplicadores positivos BigDecimal; H5. No se confía precio al cliente. |
| BR-TICKET-010 solo PAID cancelable | OK-L / P parcial | TicketServiceImpl:194-197; test PAID débil y USED negativo. |
| BR-TICKET-011 USED/CANCELLED no cancelables | OK-L / P parcial | Misma guarda; USED probado, falta CANCELLED (y RESERVED para solo PAID). |
| BR-TICKET-012 cancelar no después evento | OK-L / P | TicketServiceImpl:200-202; falta unit test específico. |
| BR-TICKET-013 solo PAID utilizable | OK-L / P parcial | TicketServiceImpl:227-230; test PAID débil y CANCELLED negativo; faltan USED/RESERVED. |
| BR-TICKET-014 CANCELLED nunca utilizable | OK-L | TicketServiceImpl:227 ya lo rechaza; :233-235 es redundante/inaccesible pero no incumple. Test :367-389 con rol STAFF configura la precondición correctamente. |

### Estado inicial y ciclos adicionales

- S §27: compra crea PAID (`TicketServiceImpl:116-124`), no RESERVED; correcto.
- S §31 describe DRAFT→PUBLISHED→SOLD_OUT→FINISHED y cancelaciones conceptuales. No obliga, fuera de los contratos mínimos, a crear métodos nuevos de Event.cancel/finish.
- No exigir reapertura SOLD_OUT al cancelar ni contar USED como PAID: no está solicitado y cambiaría reglas explícitas. Si se quisiera esa evolución, requiere decisión de producto.

## Transacciones: métodos y semántica

| Servicio | Lecturas | Escrituras | Evaluación |
|---|---|---|---|
| VenueServiceImpl | findByCode, findActiveVenues; clase :13 readOnly | — | OK-L. |
| ArtistServiceImpl | findById, findByStageName, findActiveArtists; clase :13 readOnly | — | OK-L. |
| EventServiceImpl | findByCode, findPublishedEvents, findByArtist; clase :26 readOnly | create :45, publish :124, addArtist :147 @Transactional | OK-L. publish muta entidad gestionada sin save explícito: dirty checking válido, explicado en G:1576-1585. No reportarlo como persistencia ausente. |
| UserServiceImpl | findByEmail/findByUsername; clase :20 readOnly | register :38 @Transactional, User/Profile :68-70 | OK-L; relación owning UserProfile.user asegura FK. |
| TicketServiceImpl | findByCode/findByUserEmail/findPaidTicketByEvent; clase :30 readOnly | purchase :54, cancel :180, markAsUsed :209 @Transactional | OK-L; compra y SOLD_OUT comparten transacción. El segundo conteo permite flush AUTO antes de query; no asumir count obsoleto solo porque no hay saveAndFlush. |
| AuthServiceImpl | login :102 y currentUser :188 readOnly | register :136 @Transactional | OK-L de anotaciones; H3 diseño/H7 prueba. No se requiere readOnly a nivel de clase si cada método persistente está anotado. |
| AuthorizationServiceImpl | contexto de seguridad, sin Repository | — | No requiere @Transactional: no hay operaciones persistentes. |
| PriceCalculator | cálculo puro | — | No requiere transacción. |

Todas las excepciones de negocio son RuntimeException; permiten rollback por defecto. Mockito no activa proxies, así que la presencia de anotaciones no demuestra que se ejecutó una transacción ni su rollback. La exclusión de concurrencia de S §53 se respeta: no se exigen locks ni inventario atómico productivo.

## Tests mínimos: checklist individual S §43–46

Inventario calculado desde anotaciones `@Test`: Artist 4, Venue 3, Event 8, User 4, Ticket 12, Auth 14, Authorization 15: **60 métodos en siete archivos**. De ellos, los cinco servicios originales contienen 31. No son totales de ejecución. Los 24 identificadores mínimos nominados de Event/User/Ticket tienen métodos correspondientes; cuatro necesitan reforzar la condición semántica de estado (H6).

En las filas siguientes los nombres de archivo se refieren a `T/service/impl/`.

| ID mínimo | Método y línea | Evaluación estática |
|---|---|---|
| TEST-EVENT-001 | EventServiceImplTest:58 findByCode_retornaDTO | Presente: DTO y verify lookup/mapper; estado PUBLISHED hace válida evolución SEC. |
| TEST-EVENT-002 | :84 findByCode_lanzaException | Presente: ResourceNotFound y mapper never. |
| TEST-EVENT-003 | :97 create_eventoValido | Presente: save y DTO; no captura DRAFT del argumento (AC-001/H6). |
| TEST-EVENT-004 | :130 create_venueInexistente | Presente: ResourceNotFound y save never. |
| TEST-EVENT-005 | :149 create_venueInactivo | Presente: BusinessRule y save never. |
| TEST-EVENT-006 | :171 create_fechaPasada | Presente: BusinessRule y save never. |
| TEST-EVENT-007 | :193 publish_desdeDRAFT | Débil: solo DTO mockeado PUBLISHED; no afirma Event modificado. |
| TEST-EVENT-008 | :218 publish_desdeCancelled | Presente: BusinessRule y save never. Podría además afirmar entidad intacta para cubrir dirty checking. |
| TEST-USER-001 | UserServiceImplTest:49 register_usuarioValido | Presente: DTO y saves User/Profile, pero `any` no verifica datos/relación/activo creados. |
| TEST-USER-002 | :79 register_usernameDuplicado | Presente: DuplicateResource y ambos saves never. |
| TEST-USER-003 | :97 register_emailDuplicado | Presente: DuplicateResource y ambos saves never; lookup ignorecase. |
| TEST-USER-004 | :118 register_birthDateFuturo | Presente: BusinessRule y ambos saves never. |
| TEST-TICKET-001 | TicketServiceImplTest:70 purchase_compraValida | Débil: PAID está en entidad/DTO prefabricados; save(any) no prueba creación PAID real. |
| TEST-TICKET-002 | :111 purchase_usuarioInexistente | Presente: ResourceNotFound y ticket save never. |
| TEST-TICKET-003 | :127 purchase_usuarioInactivo | Presente: BusinessRule y ticket save never. |
| TEST-TICKET-004 | :145 purchase_eventoDraft | Presente: BusinessRule y ticket save never. |
| TEST-TICKET-005 | :168 purchase_eventoCancelled | Presente: BusinessRule y ticket save never. |
| TEST-TICKET-006 | :191 purchase_menorDeEdad | Presente, pero temporalmente frágil: fecha de nacimiento fija y evento `now()+3 meses`; dejará de representar menor de 18 al avanzar el calendario. |
| TEST-TICKET-007 | :219 purchase_sinCapacidad | Presente: conteo en capacidad, BusinessRule y save never. |
| TEST-TICKET-008 | :243 purchase_ultimoTicket | Presente y comprueba efecto real: conteo 1→2, event.getStatus SOLD_OUT, ambos saves. Capacidad 2 sirve para esta regla; no es el escenario completo §47 de capacidad 3. |
| TEST-TICKET-009 | :284 cancel_ticketPaid | Débil: status del DTO simulado y save(any), no Ticket mutado. |
| TEST-TICKET-010 | :313 cancel_ticketUsed | Presente: BusinessRule y save never. |
| TEST-TICKET-011 | :335 markAsUsed_ticketPaid | Débil: status del DTO simulado y save(any), no Ticket mutado. STAFF correctamente configurado. |
| TEST-TICKET-012 | :367 markAsUsed_ticketCancelled | Presente: BusinessRule y save never; rol STAFF evita falsa causa por autorización. |

### Mockito, AssertJ, AAA y cobertura no nominada

- Los siete archivos usan MockitoExtension; SUT real con @InjectMocks; repositories y mappers mockeados. No se mockea el servicio bajo prueba. PriceCalculator como colaborador mock es correcto para TicketService, pero necesita su propio test real (H5).
- `when`, `verify`, `never`, `any` se usan en los tests originales. `eq` se importa en AuthServiceImplTest pero no se usa; S §46 lo condiciona a "cuando corresponda". No se exige introducir un matcher innecesario si se pasan valores exactos.
- Los cinco tests originales contienen secciones ARRANGE/ACT/ASSERT, a veces ACT & ASSERT juntas para excepciones. Los de seguridad separan fases con espacios/estructura, sin todos los comentarios literales; no es un defecto funcional ni exige tres comentarios por test.
- AssertJ se usa para resultados y JUnit `assertThrows` para excepciones; S no prohíbe mezclar ambos. No es obligatorio reescribirlos a assertThatThrownBy.
- Los caminos inválidos de TicketService comprueban `ticketRepository.save never`, pero normalmente no `eventRepository.save never` ni mapper/no downstream. G sesión 13 aconseja corte temprano. Ampliar verificaciones, especialmente para usuario inactivo, es cobertura adicional; no prueba que exista hoy una escritura indebida.
- Además de H4/H5, faltan tests unitarios de listados Event (publicados/artista), de búsquedas User, de lecturas Ticket y de stageName inexistente. Parte tiene cobertura HTTP real, distinguida en el checklist FR; no presentar estos métodos como totalmente sin tests.
- Faltan pruebas unitarias de reglas/ramas detalladas en la tabla BR: código duplicado/edad negativa, publicación vencida/venue inactivo, compra evento inexistente/pasado, SOLD_OUT/FINISHED, cancelar pasado/CANCELLED, usar USED/RESERVED; y fronteras de edad (cumple exactamente en el evento, un día menos, sin restricción y perfil faltante). Son brechas de evidencia, no bugs de implementación demostrados. Solo addArtist y pricing tienen exigencia específica adicional inequívoca fuera de los 24 tests mínimos nominados.
- SEC §17.1 pide AuthService con usuario inactivo y no emisión: los 14 unit tests de AuthServiceImplTest no incluyen ese caso. Sí existe integración `T/security/LoginServiceTest.java:134-142`. Añadir un unitario de enabled=false y `never().issueToken` cierra el nivel pedido sin cambiar negocio.
- `T/service/impl/AuthServiceImplTest.java:257-268` prueba 73 ASCII, pero no el caso <=72 caracteres y >72 bytes; no demuestra la razón de validar UTF-8 adicionalmente. El código sí cuenta bytes. Añadir un caso multibyte es cobertura, no nuevo requisito de política.
- `T/security/AuthorizationMatrixTest.java:376-389` afirma el tamaño de una lista literal de 20 IDs y un conteo sobre estados constantes; no prueba que las 20 rutas fueran efectivamente ejercitadas ni que se cubrieron los roles prohibidos. No sustituye la matriz de escenarios, y explica la omisión STAFF en H1/H2. Hallazgo auxiliar de calidad; el audit principal de seguridad puede consolidarlo.

## Criterios de aceptación S §47–48

| Criterio | Evaluación |
|---|---|
| AC-001 crear CMF DRAFT | Implementado EventServiceImpl:71; unitario solo indirecto; reforzar captor H6. |
| AC-002 publicación válida | Implementado :130-144; unitario débil, HTTP real positivo presente. |
| AC-003 tres artistas | Asociación repetible implementada; solo HTTP de un artista. Falta prueba del escenario con tres y unitarios H4. |
| AC-004 Andrea compra | Implementado y casos unit/HTTP; identidad propia añadida por SEC válida. |
| AC-005 Carlos compra | Último ticket unitario usa Carlos, pero escenario completo no encadenado. |
| AC-006 Laura menor rechazada | Test menor presente; fixture no estable ante paso del tiempo. |
| AC-007 Miguel inactivo rechazado | Unitario de compra presente; filtro HTTP puede denegar antes según SEC, no regresión de negocio. |
| AC-008 tercer adulto llena capacidad 3 | Lógica implementada; unitario último cupo usa capacidad 2 y conteos 1→2. Equivalente para regla individual, no prueba completa del escenario literal de tres. |
| AC-009 cuarta compra rechazada | Guardas PUBLISHED/capacidad implementadas; test capacidad llena existe, pero no secuencia de cuatro compras sobre fixture capacity=3. |
| AC-010 PAID→USED | Implementado; unitario débil H6, HTTP STAFF comprueba entidad. |
| AC-011 USED no cancelable | Unitario presente, excepción y never save. |

No se exige una integración de Service con PostgreSQL en fase S: S §4.2 la excluye. Para evidenciar el escenario completo puede utilizarse un fixture unitario coherente con conteos secuenciales sin levantar base de datos. La obligación de integración selectiva SEC §17.3 para **registro seguro** es posterior y específica (H7).

## Definition of Done de S §50: todos los ítems

| Ítem | Estado |
|---|---|
| Interfaces Service | OK-L, cinco originales. |
| Implementaciones @Service | OK-L. |
| Constructor injection | OK-L. |
| DTOs record | OK-L. |
| Sin entidades JPA expuestas | OK-L en contratos originales. |
| MapStruct Entity→DTO | OK-L; nueva compilación NV. |
| Excepciones personalizadas | OK-L. |
| Optional seguro | OK-L. |
| Reglas en Service | OK-L, H3 duplicación tras evolución SEC. |
| Escrituras transaccionales | OK-L; rollback runtime NV. |
| Lecturas readOnly cuando corresponde | OK-L. |
| VenueService implementado | OK-L. |
| ArtistService implementado | OK-L. |
| EventService implementado | OK-L, pruebas addArtist obligatorias faltantes. |
| UserService implementado | OK-L, flujo público lo sustituye H3. |
| TicketService implementado | OK-L funcional; nombre literal H8. |
| Compra valida usuario | OK-L, precondiciones SEC H2. |
| Compra valida evento | OK-L. |
| Compra valida edad | OK-L. |
| Compra valida capacidad | OK-L. |
| SOLD_OUT correcto | OK-L, prueba unitaria mutación real presente; concurrencia excluida. |
| Unit tests Mockito | OK-L. |
| Unit tests AssertJ | OK-L. |
| Sin PostgreSQL en unit tests | OK-L, siete archivos service/impl. |
| Sin SpringBootTest en unitarios Service | OK-L. |
| verify never en caminos inválidos | Presente; mejora de amplitud en todos los colaboradores pendiente, no ausencia global. |
| mvn clean test exitoso | NV; auditor principal debe adjuntar ejecución propia. |

## Correspondencia con la guía completa (no importar CourseHub como requisitos de PulsePass)

| Tramo de guía | Contenido y contraste |
|---|---|
| §§1–4, :1-159 | Objetivo y arquitectura por capas, caso CourseHub y etapas didácticas. PulsePass conserva separación. No implementar Student/Course/Enrollment. |
| Sesión 1, :165-278 | Service define casos de uso/reglas y Repository acceso a datos: cumplido salvo duplicación de registro H3. |
| Sesión 2, :282-415 | Interface+Impl/DIP y contrato útil: cinco servicios completos; nombre singular H8 es divergencia literal, no pedir nuevas interfaces para PriceCalculator puro. |
| Sesión 3, :419-477 | Dependencias final, constructor explícito o Lombok equivalente: cumplido, sin field injection. |
| Sesión 4, :481-647 | Entrada/salida mediante DTO record, no Entity: cumplido; colecciones no profundamente inmutables como consideración opcional. |
| Sesión 5, :651-802 | MapStruct componente Spring y rutas anidadas, no negocio en mapper: cumplido por lectura. |
| Sesión 6, :806-1054 | Optional, map/orElseThrow, no get inseguro; no Optional como DTO/campo entidad: cumplido. No obligación de usar todos los operadores didácticos. |
| Sesión 7, :1059-1381 | Streams y method references si mejoran claridad; no sustituir cada if: cumplido en listados y guardas. |
| Sesión 8, :1387-1585 | Servicio readOnly de clase, override escritura, alta/map/Optional/listas y dirty checking: cumplido estructuralmente. Normalización email del ejemplo CourseHub no es política obligatoria nueva de PulsePass. |
| Sesión 9, :1590-1715 | Excepciones semánticas: cumplido. BusinessException abstracta es ejemplo opcional; PRD S pide al menos tres clases concretas y existen. |
| Sesión 10, :1719-1869 | Orquestación secuencial, validaciones antes de save: compra cumplida; duplicación Auth/User H3. |
| Sesión 11, :1875-2082 | Service/Repository/DTO/Mapper/DI/DIP/Facade; Strategy/Factory cuando existe problema: PriceCalculator encapsula política sin sobrediseño. No exigir 10 patrones materializados ni factories en todas las entidades. |
| Sesión 12, :2088-2352 | MockitoExtension, mocks colaboradores, SUT real, when/verify/never/Optional y tests aislados: cumplido por lectura, con debilidad de mutaciones H6. El ejemplo de findById :2308-2319 toma DTO de un mapper mock sin stub previo: no copiar ese patrón para justificar aserciones vacías. |
| Sesión 13, :2357-2423 | Probar resultados y comportamiento/corte temprano: cobertura parcial; H4/H5/H6 y tabla BR. |
| §14, :2427-2506 | Unit vs integración, pirámide: tests separados; no exigir DB dentro de unitarios ni eliminar integraciones posteriores de seguridad. |
| §15, :2510-2571 | Organización por feature recomendada para aplicaciones medianas/grandes: no invalida el árbol por capas sugerido por PRD S §8. |
| §16, :2575-2705 | Antipatrones: no DTO.save, no mapper negocio, no get inseguro, no mock SUT, no RuntimeException genérica: no detectados. Servicios de consulta simples con semántica DTO no son wrappers indebidos por ser breves. |
| §§17–18, :2709-2821 | Proyecto/reglas CourseHub: no traducir crédito 1..6, grade 0..5 o matrícula única a tickets; PulsePass no prohíbe múltiples compras por usuario/evento. Aplicar únicamente analogías de responsabilidad/estado. |
| §§19–20, :2825-2935 | Optional/streams y filtrado en DB: listados activos y públicos delegan query filtrada; correcto. |
| §21, :2939-2954 | Reconocer patrones/técnicas: presentes sin obligación de una clase por patrón. |
| §22, :2958-3001 | Preguntas de sustentación: requieren explicación oral, no son nuevas funciones ni archivos de código obligatorios. No se evaluó conocimiento del estudiante. |
| §§23–24, :3004-3093 | Modelo mental y flujo completo: arquitectura adecuada salvo registro duplicado; DTO/mapper no ejecutan reglas. |

## Evoluciones, contradicciones y hardening separado

### Evolución intencional / no incumplimiento

1. S excluía Security/JWT/Controllers; SEC los incorpora expresamente (§3.1–3.4). Password obligatoria y precondiciones de propiedad/rol son cambios requeridos, no regresiones contra el PRD viejo.
2. Persistencia BR-010 dejaba SOLD_OUT manual (`PRD_PulsePass.md:159`); S BR-TICKET-008 exige cálculo dentro de compra. Cumplir S es evolución autorizada.
3. Persistencia usa fixture capacity 5000 (`PRD_PulsePass.md:355`), S escenario §47 capacity 3. Mantener datos propios de cada fase; test de último cupo con 2 no invalida regla, aunque no demuestra escenario completo con 3.
4. Edad del evento se maneja con LocalDateTime y cálculo sobre toLocalDate: coincide con ejemplo de S §12 y evita que la hora altere la edad.
5. SECURITY exige checks en frontera negocio para ownership; no obliga a poner todos los permisos de toda ruta dentro de EventService. ADMIN en create/publish/addArtist está protegido en SecurityConfig. Replicarlo a nivel método sería defensa adicional, no un fallo HTTP actual demostrado.

### Contradicciones o exactitud documental

- SEC §3.4 dice ErrorResponse de seis campos, su §13 muestra cinco y los ejemplos JSON contienen cinco. La forma existente de cinco es internamente consistente; no modificar PRD ni inventar sexto campo.
- S §12 usa `Integer minimumAge` en un ejemplo, implementación usa int y @Min. No se transforma una muestra de tipos en obligación exacta sin más; señalar la diferencia de nulabilidad basta. S §13 no prescribe el tipo exacto del campo de salida.
- S §32 muestra ArtistRepository.findByStageNameIgnoreCase; implementación tiene findByStageName exacto (`M/repository/ArtistRepository.java:13`). El apartado dice ejemplos y permite variación de firmas; S §17 no promete búsqueda de stageName sin case. No elevarlo a incumplimiento inequívoco. Si se exige ignorecase como política nueva, cambiar query y pruebas, no solo el nombre.
- H8 sí corresponde a contrato **mínimo Service** nombrado expresamente; no confundirlo con la flexibilidad de ejemplos Repository.

### Hardening opcional, fuera de la lista de incumplimientos obligatorios

- `M/service/impl/TicketServiceImpl.java:115` trunca UUID a ocho caracteres hex. Puede colisionar y la unicidad DB rechazará una compra: conservar más UUID o reintento explícito reduciría riesgo. No se ha observado colisión; no es un fallo de concurrencia inventado ni requisito de locking.
- Inmutabilidad profunda de colecciones record (`EventResponse.artists`, AuthenticatedUserResponse.roles) con copias defensivas, si realmente se necesita un contrato inmutable en memoria.
- Llamar `user.assignProfile(profile)` en ambos registros haría coherente el grafo en memoria antes de terminar transacción (`M/domain/User.java:80-83`). Hoy se persiste correctamente el lado owning UserProfile.user; no se ha demostrado rotura en la ruta normal registro→nuevo request compra. Es relevante si se encadenan casos de uso en la misma sesión de persistencia.
- Política temporal determinista mediante Clock para pruebas de límites; usar fechas de nacimiento relativas al evento en el test de menor para que no envejezca fuera del caso. Validar estrictamente `isAfter` para fronteras de creación/publicación si se necesita igualdad exacta; no se declara un bug reproducido de reloj.
- Tests unitarios de mappers reales y prueba de rollback de compra aportarían evidencia adicional; integración de compra está excluida explícitamente de S, por lo que no es deuda obligatoria en ese PRD.
- Reapertura de SOLD_OUT tras cancelación, conteo de USED, locks, precios dinámicos, reservas, pagos y reembolsos no se piden. No proponerlos como corrección mínima de este audit.

## Orden mínimo de atención

1. **P1 H1/H2:** cerrar políticas STAFF/USER por operación y añadir negativos que lleguen a un recurso existente con reglas de negocio satisfechas. Un 404 por recurso inexistente no acredita rechazo por rol.
2. **P2 H3:** devolver propiedad del registro de negocio a UserService, manteniendo la transacción exterior AuthService y DTO/ruta actuales.
3. **P2 H4/H5/H6:** añadir unitarios específicos de artistas/precio y corregir aserciones sobre entidades reales/captores; no multiplicar infraestructura.
4. **P2 H7:** evidencia de rollback de registro seguro, en integración aislada, sin contaminar unit tests.
5. **P3 H8:** decidir/alinear nombre mínimo con caller. Completar brechas de evidencia BR según prioridad sin reescribir pruebas que ya cubren su regla.
6. El auditor principal ejecuta suites y registra resultados propios. Solo entonces se puede cerrar la casilla de ejecución del DoD.

## Límites y artefactos

Únicamente se creó este informe y extracciones de PDFs bajo `C:/Users/57321/AppData/Local/hermes/cache/scratch/`; no se modificó el repositorio, no se editaron PRDs, no se hicieron commits, no se ejecutó Maven y no se abrió `.env`. No se validó HTTP ni rollback real en esta subauditoría. Los hallazgos de seguridad son trazados estáticos verificables, no resultados de explotación ejecutada; el auditor principal debe integrar su evidencia dinámica.


---

# Anexo C — Seguridad: matriz y Definition of Done

# Auditoría de seguridad PulsePass — solo lectura

## Veredicto

**POR CORREGIR.** Hay desviaciones obligatorias de autorización: STAFF puede leer datos personales/listados y cancelar tickets ajenos donde el PRD solo permite ADMIN; la compra no exige ROLE_USER. El validador JWT acepta tokens firmados sin expiración y con HS384 aunque el contrato exige exp y HS256. Una suite verde no demostraría conformidad con estas condiciones porque faltan las pruebas negativas correspondientes.

**COMPLETO por inspección:** están los componentes de autenticación/JJWT, BCrypt, registro HTTP transaccional, principal persistente, recarga de roles/estado, filtros activos, ocultamiento de borradores y controles de propiedad por ID. **Ejecución del padre incorporada al cierre:** BUILD SUCCESS, exit 0, 264 tests en 27 suites, cero fallos/errores/omitidos; los 143 métodos de seguridad/auth están incluidos. Esto no elimina las desviaciones que esos tests no cubren.

**CRÍTICO:** este subinforme no demuestra un P0 ni una explotación anónima. Los hallazgos fuertes son P1; la configuración de datasource/JPA/Flyway y su impacto de arranque quedan en la investigación independiente del agente padre.

## 1. Base, fuentes, alcance y evidencia

- Repositorio: `C:/Users/57321/Desktop/pulse_pass`.
- HEAD auditado: `5c9056f385f0f459f39b809029718db2b8f0ee98`, rama `feature/rebuild-pulsepass`.
- `git status --short`: únicamente entradas no rastreadas preexistentes (`EstadoSesion_Capa_Seguridad.md`, `MatrizAutorizacion.md`, `PlanAccion_Capa_Seguridad.md`, `error`, `guia/`). Sin modificaciones rastreadas ni conflictos.
- Fuente primaria leída **completa, 26 páginas**: `PRD_PulsePass_Capa_Seguridad_Spring_Security_JJWT.pdf`.
- Guía leída **completa, 62 páginas**: `guia/Guia_Didactica_Spring_Security_JJWT_Mermaid.pdf`.
- Antecedentes consultados: PRD Controladores v2 (contratos, validación, errores, pruebas y DoD) y PRD Servicios (reglas, transacciones, registros, tickets y concurrencia). README y documentos de plan no sustituyen al PRD.
- Inspección de todos los archivos `security/`, interfaces/implementaciones Auth y Authorization, métodos protegidos de User/Ticket/Event, controladores, DTO de registro/login/respuestas, entidades de identidad, repositorios, mappers pertinentes, handler, migraciones V1–V4, todas las pruebas security/auth y pruebas REST heredadas/adaptadas.
- Inventario mecánico guardado en `pulse-security-test-inventory.json`: **10 clases de seguridad/auth, 143 métodos @Test**, más su helper de tokens. Leídas sus implementaciones, no solo sus nombres. También leídas las cinco clases REST y su soporte común, las pruebas de servicios modificados y Flyway.
- Reconciliación mecánica: **20 rutas heredadas + 2 nuevas de auth**. Por controlador real: Venue 2, Event 7, Artist 3, User 3, Ticket 5, Auth 2. Event posee `GET /api/events/{eventCode}/tickets/paid`; no es un endpoint faltante en TicketController.
- **Este subagente no ejecutó Maven, la aplicación, PostgreSQL ni Docker.** El padre ejecutó la suite completa y sus verificaciones de configuración/handler; no se duplicaron. Se leyó su evidencia `pulsepass_audit_execution.json`: 264 tests, 27 suites, cero fallos/errores/omitidos, cero clases sin ejecutar y cero archivos rastreados cambiados. El padre informa BUILD SUCCESS/exit 0 y un contenedor PostgreSQL. No se leyeron archivos `.env`/`.env.properties` ni credenciales reales. Los 5 tests históricos de `FlywayMigrationOnExistingSchemaTest` no existen en las fuentes actuales: no se cuentan ni se citan como evidencia vigente.
- `read_file` falló al extraer PDF con «No shell found». Se sustituyó por extracción con pypdf vía `uv run --with pypdf`, exclusivamente a scratch. Los PDF no se modificaron.

### Pruebas aisladas nuevas, sin Spring context ni BD

Se compilaron clases puntuales de producción con `javac` hacia scratch y se llamaron directamente usando una clave aleatoria efímera y fixtures. No se imprimió clave ni JWT. Esto **no es ejecución de la suite Maven ni una prueba HTTP integrada**.

`pulse-security-probe.log` (salida final, exit 0):

```text
normal = ACCEPTED subject=7
without_exp = ACCEPTED subject=7
without_iat_jti = ACCEPTED subject=7
future_iat = ACCEPTED subject=7
HS384 = ACCEPTED subject=7
STAFF foreign owner privileged = ALLOWED
STAFF self without USER = ALLOWED
BCrypt matches over72 = true
PROBE_EXIT 0
```

`pulse-filter-probe.log` (filtro real, MockHttpServletRequest y UserDetailsService de fixture, exit 0):

```text
two separate Authorization headers, first valid = AUTHENTICATED
two separate Authorization headers, first invalid = ANONYMOUS
PROBE_EXIT 0
```

La primera ejecución del primer probe encontró una dependencia de logging ausente en su classpath; se corrigió únicamente el classpath del probe, no el proyecto. La ejecución final tuvo exit 0. Las advertencias SLF4J de proveedor ausente pertenecen al harness deliberadamente mínimo.

En las referencias siguientes, `M/` = `src/main/java/com/pulsepass/pass/` y `T/` = `src/test/java/com/pulsepass/pass/`.

## 2. Hallazgos obligatorios y corrección mínima

### S1 — P1: STAFF hereda privilegios administrativos ajenos en cuatro operaciones

- **PRD:** §11 FR-SEC-USR-02/03, TKT-03 y TKT-05; §11.1; SEC-006/007 y principio de permisos explícitos. STAFF global solo se permite para la función operativa indicada, no para cancelar ni consultar cualquier ficha/listado por usuario.
- **Raíz:** `M/service/impl/AuthorizationServiceImpl.java:58-74`, especialmente `:69`, permite STAFF o ADMIN para cualquier propietario.
- **Usos incorrectos:** `M/service/impl/UserServiceImpl.java:75-93` (`:81`, `:91`), `M/service/impl/TicketServiceImpl.java:154-169` (`:164`) y `:182-206` (`:191`). `SecurityConfig.java:131` solo exige autenticación para estas rutas.
- **Comportamiento:** con identidad STAFF válida, basta conocer email/username/ticketCode para recibir datos del tercero o cancelar su ticket PAID futuro. La cancelación sí cambia estado y persiste (`TicketServiceImpl.java:204-205`). La autorización está antes de las reglas comerciales, pero concede un permiso que no corresponde.
- **Impacto:** exposición de datos personales y compras; alteración no autorizada de tickets ajenos por personal operativo. Es una ampliación real respecto del contrato, no hardening opcional.
- **Corrección mínima:** conservar `requireOwnerOrPrivileged` para **TKT-02**, donde STAFF sí está permitido; crear una comprobación dueño-o-ADMIN y usarla únicamente en las cuatro operaciones anteriores. Para cancelación, aplicar además la distinción propietario USER/ADMIN de la matriz. No eliminar STAFF global de las rutas operativas legítimas.
- **Prueba faltante:** STAFF tercero debe obtener 403 en ambas búsquedas de usuario, listado by-user y cancelación; verificar datos no devueltos, estado PAID intacto y ausencia de escritura. ADMIN tercero debe conservar acceso permitido. Además del probe del helper, se leyó el log del padre `pulsepass-audit-authorization-probe.log`: **services reales + AuthorizationService real, repos/mappers mock**, STAFF ajeno obtiene usuario por email/username, lista tickets y cancela con estado final CANCELLED. Es reproducción ejecutada del Service, no un request HTTP/BD real; la cadena HTTP permite esos caminos por authenticated().

### S2 — P1: comprar exige identidad propia, pero no el rol USER

- **PRD:** §11 FR-SEC-TKT-01, «USER; userEmail debe pertenecer al principal»; introducción de §11: STAFF/ADMIN no son propietarios automáticos ni obtienen permisos de autoservicio no indicados.
- **Evidencia:** `M/security/SecurityConfig.java:110-131` no contiene matcher de rol para `POST /api/tickets`; `M/service/impl/TicketServiceImpl.java:56-68` solo llama `requireSelf`; `M/service/impl/AuthorizationServiceImpl.java:89-99` comprueba activo e igualdad de ID, no USER.
- **Comportamiento:** STAFF-only, ADMIN-only o una cuenta activa con credencial pero sin roles puede comprar para sí misma si cumple las reglas de negocio. El probe llama `requireSelf(7)` con solo ROLE_STAFF y pasa; el padre reprodujo además **TicketService.purchase real con STAFF sin USER**, resultado ALLOWED, en `pulsepass-audit-authorization-probe.log`. Los casos ADMIN/sin roles se infieren del mismo camino y no se presentan como ejecuciones separadas. No hay jerarquía de roles declarada que otorgue USER implícitamente.
- **Impacto:** incumple la matriz y permite seguir comprando después de retirar el rol USER; la recarga de roles por request no resuelve una regla que nunca consulta el rol.
- **Corrección mínima:** matcher `POST /api/tickets` con `hasRole("USER")`, antes del fallback, y comprobación equivalente en la frontera de servicio si se exige igual protección al acceso directo. No cambiar `requireSelf` genéricamente a dueño-o-privilegiado: abriría compras a nombre de terceros.
- **Pruebas faltantes:** USER propio 201; USER ajeno 403 sin escritura; STAFF-only/ADMIN-only/sin roles propios 403; USER+ADMIN propio permitido. No confundir los tests actuales «STAFF no puede comprar para tercero» con «STAFF no puede comprar sin USER».

### S3 — P1: validación JWT no impone exp ni HS256

- **PRD:** FR-SEC-AUTH-006; §12.1 exp obligatoria/futura; §12.2 HS256 explícito y comparación de algoritmo; §14.4 «imponer exp, sub y algoritmo esperado»; DoD §22.
- **Evidencia:** `M/security/JwtServiceImpl.java:121-148`: parsea firma e issuer y comprueba sub, pero no obtiene/valida exp ni header.alg. `EXPECTED_ALGORITHM` (`:62`) solo sale por `expectedAlgorithm()` (`:189-190`), no participa en validar.
- **Resultado observado:** el validador devuelve sujeto 7 para un JWT firmado sin exp y para uno HS384 firmado con la misma clave de 48 bytes. La emisión propia sí usa HS256 (`:100`) y exp (`:98`), pero eso no establece restricciones sobre tokens recibidos.
- **Impacto:** acepta tokens fuera del contrato, incluso sin caducidad. La firma sigue verificándose: **no es un bypass con firma falsa o alg=none**, ni significa que alguien sin clave pueda crear esos tokens.
- **Corrección mínima:** conservar el objeto JWS para validar su algoritmo igual a HS256 (o allowlist del parser), exigir exp no nula y futura, y mantener issuer/sub/parseSignedClaims. No sustituir JJWT ni crear otro servicio.
- **Cobertura engañosa:** `T/security/JwtServiceTest.java:89` y `T/security/LoginServiceTest.java:177` comparan el getter con la constante; no verifican el algoritmo del token ni rechazo de HS384. Faltan tests negativos sin exp y con otro algoritmo soportado, usando clave suficientemente larga para que el rechazo no sea accidental por clave débil.

### S4 — P2: claims de metadatos aceptados sin contrato temporal definido

- **PRD:** §12.1 iat y jti obligatorios, iat sujeto a política de fechas aceptables; §7.1/§14.4 validación de fechas.
- **Evidencia:** `M/security/JwtServiceImpl.java:127-139`; emisión sí asigna iat y jti (`:97`, `:99`), validación no los comprueba. `JwtProperties.java:36-50` tampoco expresa una política de skew/iat.
- **Resultado observado:** acepta token sin iat/jti y otro con iat una hora futura.
- **Impacto:** trazabilidad/contrato de tokens incompletos; no confundir ausencia de jti con necesidad de una denylist, que el MVP excluye.
- **Corrección mínima:** exigir iat/jti no vacíos, documentar una tolerancia temporal razonable y validar iat contra ella, y coherencia iat/exp. Validar configuración de issuer no vacío y TTL positivo evita emitir tokens inútiles por mala configuración, aunque este último punto es robustez adicional.
- **Tests:** sin iat, sin jti, jti vacío, iat futuro fuera de tolerancia; emisión con metadatos y rechazo separado del fallo de firma.

### S5 — P2: Bearer con dos headers se interpreta según el primero; falta el test obligatorio

- **PRD:** §17.1 JwtAuthenticationFilterTest, doble header sospechoso y no sobrescribir contexto inseguramente; NFR-SEC-002. La política exacta de rechazo de duplicados no está detallada por el PRD, pero el propio comentario promete que no pasará un header sospechoso.
- **Evidencia:** `M/security/JwtAuthenticationFilter.java:68` usa `getHeader`, no inspecciona `getHeaders`; `:85-87` describe un tratamiento que no detecta duplicados separados. `:63-65` deja intacto cualquier Authentication preexistente.
- **Resultado observado:** con primer header válido y segundo inválido autentica; al invertirlos queda anónimo. El token válido sí es verificado: no es una falsificación de identidad sin credencial.
- **Impacto:** interpretación ambigua entre proxy/servidor y ausencia de cobertura de una entrada expresamente enumerada.
- **Corrección mínima:** decidir/rechazar más de un Authorization antes de extraer Bearer, con resultado uniforme. Añadir el test unitario del filtro (sin header, válido, inválido, duplicados en ambos órdenes, contexto preexistente legítimo y contexto no autenticado). No se exige modificar el patrón válido de dejar continuar requests sin header.

### S6 — P2: faltan auditoría de login/denegaciones y request ID — requisito explícito, no opcional

- **PRD:** §16 **NFR-SEC-006**: registrar login correcto/fallido y denegaciones sin credenciales y **con request ID**.
- **Evidencia:** `M/service/impl/AuthServiceImpl.java:103-119` no registra ninguno de los dos eventos; `M/security/RestAuthenticationEntryPoint.java:45-59`, `RestAccessDeniedHandler.java:44-58` y `M/service/impl/AuthorizationServiceImpl.java:58-99` no registran denegaciones. Los únicos logs de seguridad encontrados son clases de excepción en DEBUG (`JwtAuthenticationFilter.java:142`, `JwtServiceImpl.java:145`), sin request ID. No hay MDC/filtro de correlación en el código revisado.
- **Impacto:** no se pueden correlacionar autenticaciones y rechazos ni cumplir la trazabilidad prometida.
- **Corrección mínima:** un request ID seguro generado/propagado por request y logs acotados en éxito/fallo/denegación. Nunca incluir password, hash, clave o Bearer. Probar campos permitidos y que no se registren secretos; no añadir una plataforma de observabilidad completa.

### S7 — P2: política CORS no parametrizada — requisito explícito, no «CORS avanzado» opcional

- **PRD:** §16 **NFR-SEC-008**, «Emisor, duración, clave y política CORS parametrizados». §16.1 recomienda orígenes explícitos para producción; no convierte la parametrización básica de §16 en opcional.
- **Evidencia:** `M/security/SecurityConfig.java:49-136` no configura CORS ni recibe parámetros de política; `M/security/JwtProperties.java:36-50` solo modela JWT. No hay `CorsConfigurationSource`, `WebMvcConfigurer` CORS ni `@CrossOrigin` en las fuentes revisadas.
- **Impacto:** falta configuración prevista y pruebas de preflight. Ausencia de CORS no prueba que cualquier origen tenga acceso; el comportamiento por defecto puede denegar cross-origin y no constituye por sí solo filtración.
- **Corrección mínima:** parametrizar orígenes/métodos/headers permitidos y conectar esa política a la cadena; default restrictivo. Pruebas OPTIONS de origen admitido y rechazado. No abrir `*` ni introducir cookies por este motivo.

### S8 — P2: no hay medición de carga de autenticación exigida

- **PRD:** §16 **NFR-SEC-003**, «Evitar consultas N+1 para principal y roles; medir carga de autenticación».
- **Evidencia:** `JwtAuthenticationFilter.java:120-123` llama por separado `loadAuthorities` y `loadPrincipalById`; `CustomUserDetailsService.java:140-150` vuelve a resolver credencial/usuario, y `:120-129` vuelve a buscar por ID. Los repositorios sí hacen consultas dirigidas (`UserRoleRepository.java:13`, `UserCredentialRepository.java:12-14`); no hay un findAll de roles.
- **Conclusión precisa:** no se demuestra un N+1 ilimitado por roles. Sí hay relecturas potencialmente redundantes y **ninguna prueba/informe de número de consultas o carga/latencia**. No asignar un conteo SQL inventado: depende del contexto transaccional y caché de persistencia.
- **Corrección mínima:** medir consultas y latencia por request autenticado (también por email y con varios roles), documentar resultado; si hay duplicaciones evitables, devolver principal+roles en una carga transaccional. Mantener revalidación de estado/roles.

### S9 — P2: la prueba llamada «registro atómico» no demuestra rollback; integración incompleta

- **PRD:** FR-SEC-AUTH-001, NFR-SEC-004, §17.3 y DoD: persistir usuario/hash/rol atómicamente y probar integración selectiva.
- **Evidencia positiva:** `M/service/impl/AuthServiceImpl.java:135-177` tiene `@Transactional`; `UserController.java:39-42` delega allí. La implementación HTTP no crea deliberadamente cuentas sin credenciales.
- **Brecha:** `T/service/impl/AuthServiceImplTest.java:161-196` usa repositorios y encoder mock. Verifica llamadas/hash ficticio/rol, no una transacción real ni rollback. `T/controller/UserControllerTest.java:42-84` verifica respuesta pero no recarga hash/rol; `AuthorizationMatrixTest.java:197-210` solo exige 201. No hay inyección de fallo al guardar credencial/rol y comprobación desde otra transacción de que no quedaron User/Profile/Credential/Role.
- **Impacto:** DoD no demostrado ante fallo parcial; no se afirma que @Transactional esté roto.
- **Corrección mínima:** un test de registro real que verifique BCrypt con matches, perfil y solo USER, y un test de fallo tardío que pruebe rollback de las cuatro escrituras sin quedar en una transacción de test rollback-only. Probar además intento de inyectar roles por JSON.

### S10 — P2: AuthService duplica reglas que el PRD mantiene en UserService

- **PRD:** §3.2/§7.1 y §9.3: UserService.register sigue aplicando unicidad/reglas; AuthService orquesta creación y hash en una única transacción.
- **Evidencia:** `M/service/impl/AuthServiceImpl.java:131-133` declara explícitamente que replica las reglas; implementación `:140-166`. Se duplican en `M/service/impl/UserServiceImpl.java:39-72`. No hay invocación AuthService → UserService.
- **Impacto:** dos caminos de registro divergentes; una llamada directa al servicio histórico crea cuenta/perfil pero ignora la nueva password, credencial y rol. No es un bypass HTTP actual: el controlador llama al orquestador correcto.
- **Corrección mínima:** reutilizar la creación/reglas de UserService desde AuthService dentro de la transacción exterior, recuperar/referenciar usuario por su ID y guardar credencial/rol; evitar reescribir reglas. La alternativa requiere decisión explícita de contrato y no solo un comentario de etapa.

### S11 — P2: restricciones de credenciales y esquema están parcialmente probadas, no toda la migración DoD

- **PRD:** §9.2, §17.3 y §22: constraints y Flyway sobre esquema nuevo **y ya migrado**.
- **Evidencia:** `src/main/resources/db/migration/V4__security_credentials_roles.sql:47-98` tiene PK/FK/NOT NULL de credencial, UNIQUE de usuario+rol y CHECK de catálogo; no introduce contraseñas por defecto. `T/FlywayMigrationTest.java:44-73` solo comprueba contexto/repositorios, seed V2, y un método V3 sin aserción. No hay escenario explícito V1–V3 con datos → V4 ni lectura de historial/constraints de V4.
- **Cobertura insuficiente:** `T/SecurityCredentialsTest.java:111-117`, `shouldRejectRoleOutsideCatalogue`, envía **null** y solo prueba NOT NULL, no el CHECK contra un rol no admitido. `:55-61` comprueba prefijo de un hash ficticio, no BCrypt verificable. Los casos restantes sí intentan persistencia 1:1, duplicados y usuarios legados, pero su éxito se debe obtener de la ejecución del padre.
- **Desviación estructural:** PRD §9.2 pide PK compuesta en user_roles. V4 `:74-79` y `M/domain/UserRole.java:28-36` usan PK surrogate + UNIQUE(user_id, role). La unicidad de negocio está preservada; **no es una vulnerabilidad ni falta de exclusividad**, pero no es literalmente una PK compuesta y requiere aceptación explícita o alineación. No reescribir migraciones ya aplicadas: cualquier cambio futuro debe ser incremental.
- **Corrección mínima de evidencia:** pruebas selectivas de catálogo SQL/constraints y de actualización V3→V4 con datos, de no creación de credenciales legadas, y rol inválido no nulo mediante SQL. La configuración YAML detectada por el padre puede invalidar además la premisa de Hibernate validate; no se da por satisfecha.

### S12 — P2: el cierre de matriz y ciertas regresiones se afirman sin probarse

- **PRD:** SEC-012, NFR-SEC-005/010, §17.2 T-SEC-003/008/019/020, §17.4 y DoD.
- **Evidencia:** `T/security/AuthorizationMatrixTest.java:375-389`, `allTwentyRoutes_wereExercised`, cuenta una lista literal de 20 nombres y un ejemplo fijo de status; no deriva nada de los requests ni de handlers reales. Seguiría verde aunque se eliminara una prueba de ruta.
- `T/security/LoginHttpTest.java:327-337` se llama «neverInvokesBusiness» pero no verifica interacciones ni ausencia de efectos. Lo mismo ocurre para rechazos de creación/publicación (p.ej. `T/controller/EventControllerTest.java:84-94`, `:248-257`): solo comprueban status/campos, sin spy/never ni estado persistido.
- `T/controller/VenueControllerTest.java:81-86` se llama `findByCode_unexpectedException_returns500`, pero solicita un recurso inexistente y exige **404**. Se perdió una comprobación real de 500; no está probado el handler inesperado.
- Falta el test unitario del filtro entero. `CustomUserDetailsServiceTest` está en la sección de unitarias del PRD pero la clase real hereda Testcontainers (`:31-32`): tiene evidencia integrada útil, no aislamiento unitario. `AuthServiceImplTest` no contiene caso de login inactivo, aunque sí lo cubre LoginServiceTest integrado.
- **Corrección mínima:** matriz parametrizada de datos/rutas/actores que cuente ejecuciones reales; completar cruces de §3; assertions de estado o spies de que no llega a negocio; recuperar prueba de excepción inesperada con Service mock. El PRD F4 permite `@SpringBootTest` para escenarios de seguridad: no se clasifica su uso como bug; sí se reporta la pérdida concreta de evidencia previa y el requisito de no eliminar regresiones.

## 3. Matriz de las 20 rutas: contrato, implementación y cobertura

Convenciones: A=anónimo, U=USER, S=STAFF-only, D=ADMIN-only; propio/ajeno compara ID persistente. «Tests» identifica **casos escritos e inspeccionados**; al cierre se confirmó que todos los tests actuales pasaron en la ejecución del padre (no iniciada por este subagente). La tabla distingue lo que realmente afirman y lo que no cubren, aun estando verdes. Rutas públicas no tienen «rol prohibido» por definición; la prohibición solo aplica a la variante privada/no publicada.

| ID | Verbo/ruta | Controller → Service | Regla PRD §11 | Implementación y tests existentes / faltantes |
|---|---|---|---|---|
| VEN-01 | GET /api/venues/{code} | Venue → findByCode | Público | OK matcher :81; Matrix :119 A/U y VenueControllerTest :26 éxito JSON. Sin rol prohibido aplicable. |
| VEN-02 | GET /api/venues/active | Venue → findActiveVenues | Público | OK; Matrix :127 A/S y VenueControllerTest :52/:67. Prueba explícita requerida presente. |
| EVT-01 | POST /api/events | Event → create | ADMIN | OK en HTTP SecurityConfig :110; Matrix :139/:146/:153 A401/U403/D201, LoginHttp :284 D201 y EventControllerTest :97. Falta S403 y prueba de no ejecutar/mutar con denegación. |
| EVT-02 | GET /api/events/{eventCode} | Event → findByCode | PUBLISHED público; demás S/D | OK EventServiceImpl :95-102/:112-114; DraftVisibility :124/132/140/149/161 prueba A publicado, A/U draft oculto y S/D draft visible. Faltan otros estados no publicados. |
| EVT-03 | GET /api/events/published | Event → findPublishedEvents | Público | OK consulta por PUBLISHED; Matrix :164 y DraftVisibility :212 prueban A200/lista filtrada. No hay rol prohibido. |
| EVT-04 | PATCH /api/events/{eventCode}/publish | Event → publish | ADMIN | OK matcher :111; Matrix :315 A401/U403/S403/D404 y EventControllerTest :235 D200 con transición. Falta no invocar/mutar cuando U/S denegados. |
| EVT-05 | POST /api/events/{eventCode}/artists/{artistId} | Event → addArtist | ADMIN | OK matcher :112; Matrix :329 A401/U403/S403/D404, EventControllerTest :281 D200 con asociación real. Falta evidencia de ausencia de efecto con denegación. |
| EVT-06 | GET /api/events/by-artist?stageName | Event → findByArtist | Solo publicados A/U; S/D todos | OK filtro en query EventServiceImpl :174-178; DraftVisibility :175/:185/:198 A/U/S. Falta D con draft y otros estados. El matcher variable está antes del literal (:101/:105), pero ambos permitAll: no hay fallo de autorización actual por ese orden. |
| ART-01 | GET /api/artists/{id} | Artist → findById | Público | OK; Matrix :174 A404 es smoke; ArtistControllerTest :22 A200 JSON real. No confundir el 404 de smoke con prueba de éxito. |
| ART-02 | GET /api/artists/by-stage-name?stageName | Artist → findByStageName | Público | OK; Matrix :181 A404 y ArtistControllerTest :48 A200. |
| ART-03 | GET /api/artists/active | Artist → findActiveArtists | Público | OK; Matrix :187 A200 y ArtistControllerTest :67/:83 filtrado activo. |
| USR-01 | POST /api/users | User → AuthService.register | Público; solo USER; atómico | Anotación/transacción/hash implementados; Matrix :197 y UserControllerTest :42 201. AuthServiceImplTest :162 verifica rol con mocks. Falta persistencia de rol/hash+rollback e inyección de roles (S9). |
| USR-02 | GET /api/users/by-email?email | User → UserService.findByEmail | Propio o ADMIN | **Falla S1:** STAFF ajeno permitido. Matrix :218 A401/Upropio200; Ownership :271 Uajeno403, :288 propio200, :296 Dajeno200. Falta S403. |
| USR-03 | GET /api/users/by-username?username | User → findByUsername | Propio o ADMIN | **Falla S1:** STAFF ajeno permitido. Matrix :228 A401/Upropio200; Ownership :281 Uajeno403. Faltan S403 y Dajeno200. |
| TKT-01 | POST /api/tickets | Ticket → purchase | USER y email propio | **Falla S2:** no exige USER. Matrix :240 A401/U404 solo smoke; Ownership :128 Uajeno403 y count0, :150 Upropio201. Faltan S/D/sin roles propios403 y USER+rol privilegiado propio permitido. |
| TKT-02 | GET /api/tickets/{ticketCode} | Ticket → findByCode | Dueño o STAFF/ADMIN | Implementación correcta por ID: TicketServiceImpl :141-150; Matrix :259 A401/U404; Ownership :171 Uajeno403, :186 propio200, :197 Sajeno200. Falta Dajeno200. |
| TKT-03 | GET /api/tickets/by-user?email | Ticket → findByUserEmail | Dueño o ADMIN | **Falla S1:** STAFF ajeno permitido. Matrix :266 A401/Upropio200; Ownership :213 Uajeno403, :225 propio200. Faltan S403 y Dajeno200. |
| TKT-04 | GET /api/events/{eventCode}/tickets/paid | Event → TicketService.findPaidTicketByEvent | STAFF/ADMIN | OK matcher :117-118; Matrix :287 A401/U403/S200/D200. Los 200 son listas vacías sin evento; falta contenido PAID real y no ejecutar consulta sensible con rol rechazado. Servicio no tiene check directo: defensa en profundidad adicional, no fallo HTTP demostrado. |
| TKT-05 | PATCH /api/tickets/{ticketCode}/cancel | Ticket → cancel | Dueño USER o ADMIN | **Falla S1:** STAFF ajeno puede cancelar. Matrix :276 A401/U404; Ownership :239 Uajeno403+PAID, :254 propio200+CANCELLED. Faltan Sajeno403, Dajeno200 y restricción de propietario sin USER según matriz. |
| TKT-06 | PATCH /api/tickets/{ticketCode}/use | Ticket → markAsUsed | STAFF/ADMIN | OK matcher :119-120 y check de Service :221; Matrix :301 A401/U403/S404; Ownership :311 U403, :322 S200+USED. Falta D200 con recurso real, rol revocado con mismo JWT y segunda utilización rechazada en HTTP. |

**Cobertura de roles vigente no es plena:** hay pruebas de actualización de roles al cargar UserDetails (`CustomUserDetailsServiceTest.java:111-125`), pero no retirada de un rol entre dos requests HTTP usando el **mismo** JWT. `LoginHttpTest.java:361-375` comprueba un token firmado para una cuenta ya inactiva; es útil, pero no ejecuta activa→emitir→desactivar→request con mismo token. Esas transiciones explícitas cierran SEC-011/UC-SEC-10 con evidencia más fuerte.

## 4. Checklist FR de autenticación (§10)

| Requisito | Dictamen | Evidencia / límite |
|---|---|---|
| AUTH-001 registro público atómico BCrypt USER 201 | Implementado; evidencia integrada incompleta | UserController :39-42; AuthServiceImpl :135-177; S9/S10. |
| AUTH-002 username/email+password → JWT/metadatos | Implementado | AuthServiceImpl :103-116; CustomUserDetailsService :153-155; LoginServiceTest :75/:93 y LoginHttp :97. |
| AUTH-003 password incorrecta 401 genérico | Implementado | AuthServiceImpl :117-118; GlobalExceptionHandler :94-103; LoginHttp :115/:135 compara respuestas. Igualación temporal no probada, es hardening adicional. |
| AUTH-004 inactivo no recibe token/no accede | Implementado | login :107-108; CustomUserDetailsService :125-126; LoginServiceTest :134; LoginHttp :361. Falta transición real descrita arriba. |
| AUTH-005 emisión sub/iss/iat/exp/jti, firma y TTL | Implementado | JwtServiceImpl :90-104; JwtServiceTest :71-108/:127. sub ID, no username. |
| AUTH-006 rechazo vencido/manipulado/issuer/malformado/algoritmo | **No cumple completo** | Rechazos usuales cubiertos por JwtServiceTest :147-247 y LoginHttp :316-345; S3/S4 prueban faltantes. |
| AUTH-007 solo header Bearer, no query | Implementado por lectura | JwtAuthenticationFilter :68/:89-98 solo lee header. Falta test query-only/cookie-only y header duplicado; S5. |
| AUTH-008 stateless, JSESSIONID no autentica | Implementado por configuración | SecurityConfig :58/:62-63; falta prueba explícita reutilizando JSESSIONID sin Bearer. |
| AUTH-009 principal persistido y authorities confiables | Implementado | filtro :116-128; carga por ID :120-129 y roles :140-144 de CustomUserDetailsService. No usa roles del request/token. |
| AUTH-010 /auth/me mínimo sin secretos (Should) | Implementado | AuthController :51-53; AuthServiceImpl :189-204; LoginHttp :386-394; protege por fallback autenticado. |

## 5. Checklist principios SEC-001..012 (§8)

| Principio | Estado |
|---|---|
| SEC-001 denegar por defecto | Sí por lectura: anyRequest().authenticated(), no fallback público. |
| SEC-002 solo rutas aprobadas públicas | Rutas actuales sí; los comodines GET venues/artists son amplios como ejemplo PRD. Restringir futuros endpoints es hardening, no fuga actual demostrada. |
| SEC-003 BCrypt y no passwords/logs | BCrypt correcto; sin logs de secretos en archivos inspeccionados. No se abrió entorno real. |
| SEC-004 clave desde entorno | Diseño JwtProperties+resolveKey correcto y sin default en código; arranque real/configuración lo confirma el padre. |
| SEC-005 firmado, exp e issuer | Firma/issuer/emisión sí; validación incompleta S3. |
| SEC-006 identidad no confiada al cliente | Ownership por ID correcto; política de roles excedida S1/S2. |
| SEC-007 USER solo recursos propios | Comprobaciones implementadas y pruebas own/third-party; no implica que permisos STAFF/ADMIN sean correctos. |
| SEC-008 JWT inválido no concede acceso | Inválidos comunes rechazados; token fuera de contrato aceptado S3/S4. |
| SEC-009 401/403 uniformes | Estructura de cinco campos consistente por lectura; LoginHttp cubre parte; falta validar cuerpo completo incluyendo timestamp/details en ambas rutas de error. |
| SEC-010 separar auth/authz/negocio | Mayormente; AuthService replica UserService (S10). |
| SEC-011 inactivo pierde acceso siguiente request | Código correcto; escenario HTTP con cuenta inactiva, no transición real todavía. |
| SEC-012 rutas/roles/ownership/regresión probados | Parcial; S9/S11/S12 y tabla de rutas. |

## 6. Checklist NFR-SEC-001..010 (§16)

| NFR | Dictamen | Evidencia / mínimo pendiente |
|---|---|---|
| 001 confidencialidad | Parcialmente comprobado por lectura | Payload sin datos sensibles, responses sin hash y logs de excepciones solo por clase; JwtServiceTest :112 y LoginHttp :202. No inspección de secretos reales ni certificación de todo historial. AuthResponse es excepción intencional a «sin JWT en respuestas»: login necesariamente entrega el token. |
| 002 Bearer malformado no 500 | Implementado para entradas usuales | catch Exception filtro :130-143 y LoginHttp :341; duplicados sin política/test S5. |
| 003 rendimiento/no N+1 y medición | **Pendiente obligatorio** | Consultas dirigidas; no prueba de carga ni conteo SQL. S8, no afirmar N+1 probado sin medir. |
| 004 integridad transaccional | Código presente, falta prueba de fallo/rollback | @Transactional de registro; S9. |
| 005 20 respuestas exitosas compatibles | Parcial en cobertura; suite actual verde | 20 rutas y DTOs preservados; la suite del padre pasa, pero falta evidencia de algunas respuestas, p.ej. paid tickets poblados. |
| 006 logging/request ID | **Falta obligatorio** | S6. Antes era evolución en PRD Controladores; F4 lo convierte en requisito explícito. |
| 007 HTTPS fuera de laboratorio | No verificado | No configuración TLS/requireSecure ni despliegue de proxy revisado. Es obligación de entorno real, no imponer TLS a MockMvc/localhost. Falta documentar y demostrar terminación TLS antes de uso externo. |
| 008 issuer/TTL/clave/CORS parametrizados | Parcial: JWT sí, CORS falta | JwtProperties y application.yml JWT, S7. No rebajar CORS básico a opcional. |
| 009 no registro público privilegiado | Implementado | DTO sin rol; registro fuerza Role.USER :175; falta prueba integrada con payload intentando roles. |
| 010 matrices y regresión | **Parcial** | 143 tests auth/security escritos no significan cobertura de todos los cruces. S12/tabla de rutas. |

## 7. Casos T-SEC-001..020 (§17.2)

| Caso | Evidencia escrita inspeccionada | Límite |
|---|---|---|
| 001 A published 200 | LoginHttp :229; Matrix :164 | Presente. |
| 002 A create 401 | LoginHttp :237; Matrix :139 | Presente. |
| 003 U create 403 y no Service | LoginHttp :258; EventControllerTest :84 | 403 sí; no Service/no mutación no se demuestra explícitamente. |
| 004 D create 201 | LoginHttp :284; EventControllerTest :97 | Presente. |
| 005 login correcto 200 | LoginHttp :97 | Presente. La tabla §18 lo liga también a registro, que no sustituye. |
| 006 login incorrecto401 | LoginHttp :115/:135 | Presente, mensaje genérico. |
| 007 expirado401+ErrorResponse | LoginHttp :316 | status/message sí; faltan assertions de todo el record. |
| 008 firma alterada401 sin Service | LoginHttp :327 | status sí; no Service no verificado por interacción. |
| 009 ticket ajeno403 | Ownership :171 | Presente. |
| 010 compra ajena403 y0tickets | Ownership :128 | Presente. |
| 011 cancela propio200 | Ownership :254 | Presente, estado CANCELLED recargado. |
| 012 USER use403 | Ownership :311 | Presente. |
| 013 STAFF use200 | Ownership :322 | Presente, estado USED. |
| 014 ADMIN publica200 | EventControllerTest :235 | Presente, estado PUBLISHED. |
| 015 USER otros datos403 | Ownership :271/:281 | Presente. |
| 016 cuenta inactiva401 | LoginHttp :361 | Presente con usuario ya inactivo; no transición activa→inactiva. |
| 017 A draft no expuesto | DraftVisibility :132/:225 | Presente, 404 coherente. |
| 018 autorizado inválido400 | EventControllerTest :114; TicketControllerTest :126 | Presente. Padre confirmó además que minimumAge primitivo omitido/null se rechaza por Jackson3: **no reportar aceptación silenciosa de cero**. |
| 019 no stack/password/hash en error | LoginHttp :115 y :202 (éxito sin hash) | Mensajes genéricos por lectura, pero faltan assertions de ausencia en cuerpos de error completos y logs. |
| 020 regresión previa con identidad | Cinco clases ControllerTest con ControllerHttpSupport | Adaptadas con filtros reales y verdes en ejecución del padre; se perdió test500 (S12) y faltan ciertas assertions/contenido. |

### Unitarias e integración selectiva (§17.1/§17.3)

- JwtServiceTest: 22 métodos; positivos, expirado, issuer, firma distinta, payload alterado, vacío/malformado/unsigned/sin sub y clave débil. Faltan exp ausente, HS384/HS512 válidamente firmados, iat/jti y sub no numérico; no sustituir por el getter de algoritmo.
- AuthServiceImplTest: 14; login positivo/email/password erróneo/inexistente, registro y currentUser. Falta login inactivo unitario; registro «atómico» solo mocks.
- AuthorizationServiceImplTest: 15; compara ID, activo, dueños/terceros y autoridades. El helper común permite STAFF, apropiado para TKT-02; faltan políticas diferenciadas por operación, no basta probar ese helper.
- CustomUserDetailsServiceTest: 9 integrados, no unitarios; username/email, inexistente, múltiples roles, actualización, inactivo, ID y BCrypt. Falta prueba directa del camino `loadPrincipalById/loadAuthorities` usado por filtro con cambio de rol en HTTP.
- JwtAuthenticationFilterTest: **no existe**. Casos HTTP cubren parcialmente ausencia/válido/inválido; duplicados y contexto previo no tenían cobertura.
- LoginServiceTest 10 y LoginHttpTest 17 ejercitan servicios reales y cadena, con claves exclusivamente ficticias del test.
- OwnershipAntiIdorTest 15 y DraftVisibilityTest 10: buena cobertura base own/third-party y draft, pero faltan los cruces privilegiados que esconden S1/S2.
- AuthorizationMatrixTest 23 incluye una aserción tautológica de conteo (S12); sus smokes con 404 no prueban DTO exitoso.
- SecurityCredentialsTest 8: persistencia y constraints parciales; S11 distingue CHECK vs NOT NULL.

## 8. Definition of Done (§22) — cada criterio

**No se declara DoD completo.** Estados «implementado» provienen de lectura y escenarios escritos; ejecución de la suite proviene del padre y su JSON de resultados. Este subagente no ejecutó Maven.

| # | Criterio | Dictamen y evidencia |
|---|---|---|
| 1 | 20 rutas y respuestas exitosas | Rutas 20/20 contadas; DTO/status presentes; suite actual verde del padre, con brecha de contenido/cobertura S12. |
| 2 | registro usuario+hash+USER atómico | Código @Transactional presente; falta rollback real y lectura de hash/roles S9. |
| 3 | login JWT y erróneo401 | Código/tests presentes; validación completa de JWT falla S3. |
| 4 | JWT valida firma/issuer/sub/exp/algoritmo | **No cumple:** S3 observado. |
| 5 | stateless/Bearer salvo públicos | Implementado en configuración; faltan pruebas cookie/query/session; duplicados S5. |
| 6 | USER/STAFF/ADMIN reglas probadas | **No cumple completo:** S1/S2 y cruces ausentes. |
| 7 | ningún USER consulta/compra/cancela terceros | Código y tests de terceros presentes; no confundir con permisos STAFF erróneos. |
| 8 | no drafts/datos privados en GET público | Filtrado draft y datos personales autenticados por lectura/tests; STAFF puede obtener privados fuera de permiso (no es GET anónimo). |
| 9 | ErrorResponse uniforme400/401/403/404/409/500 | Record/handlers presentes con cinco campos; test500 perdido y pruebas incompletas; handler general al padre. |
| 10 | no secreto hardcodeado/plaintext/exposición | En fuentes seguridad inspeccionadas sí: BCrypt/env, payload mínimo y respuestas; no se leyeron secretos reales ni todo historial. |
| 11 | Flyway nuevo y ya migrado | **No demostrado:** S11; además padre investiga YAML. No asumir que levantar contexto prueba upgrade. |
| 12 | unitarias/MockMvc con filtros/integración pasan | **Sí para la suite actual:** padre BUILD SUCCESS/exit 0; 264 tests en 27 suites, cero fallos/errores/omitidos, sin clases actuales no ejecutadas. Filtros no deshabilitados por lectura. No incluye los negativos faltantes ni el upgrade Flyway ausente. |
| 13 | reglas de negocio intactas salvo explícitas | No se detectó relajación comercial en los métodos leídos; AuthService duplica reglas S10; comparación histórica total al padre. Concurrencia avanzada es exclusión explícita heredada. |
| 14 | README variables/roles prueba/endpoints/comandos | README :133-148 variables, :167-176 roles, :178-225 endpoints, :87-93 comando. Falta procedimiento concreto de provisión privilegiada/activación legada segura; se enuncia proceso controlado sin implementarlo/describir sus pasos. No hay usuarios privilegiados de prueba persistidos por defecto, lo cual es correcto. |

## 9. Guía didáctica: correspondencia sin imponer contradicciones

- Guía pp.15–18 explica BCrypt/UserDetailsService/DaoAuthenticationProvider/AuthenticationManager. El PRD §5.1 permite «o equivalente compatible»; `AuthServiceImpl.login` usa UserDetailsService+PasswordEncoder.matches y comprueba enabled: **no es defecto por no inyectar AuthenticationManager**. No se exige copiar una fase educativa si el contrato funciona.
- Guía pp.8/21–25 usa `sub=username`, roles en claims; PRD §9.1/§12 exige `sub=ID persistente` y roles/estado actuales de BD. La implementación sigue el PRD correctamente. **No añadir roles al token ni cambiar sub a username para coincidir con el ejemplo.**
- Guía pp.16/43–46 muestra usuarios in-memory y luego PostgreSQL con AppUser/password en la entidad. El PRD especifica UserCredential separado; esa separación es correcta. No exigir usuarios in-memory ni passwords de demostración hardcodeados.
- Guía pp.42/55 propone ORGANIZER como ejemplo/ejercicio; PulsePass MVP solo USER/STAFF/ADMIN y creación ADMIN. **ORGANIZER no es requisito de esta auditoría.**
- Stateless/filtro/contexto/Bearer y división JJWT vs Spring Security coinciden conceptualmente con guía pp.27–37/61–62. La muestra educativa no impone validación de todos los claims; PRD §14.4 sí, y la implementación no la completa (S3).
- Guía p.43 tiene un diagrama con error Mermaid impreso. Es un problema del documento educativo, no un defecto del código a corregir.
- PRD §3.4 dice «seis campos» de ErrorResponse, pero §13 y antecedente Controladores §13 enumeran **cinco**: timestamp/status/error/message/details. El record real tiene cinco y es coherente. No inventar un sexto campo ni usar este lapsus para marcar un error del programa.

## 10. Opcional / operativo / fuera de alcance — no mezclar con incumplimientos

**Requisitos explícitos ya pendientes:** logging/request ID (NFR006), medir carga de autenticación (NFR003), CORS parametrizado (NFR008), matriz de 20 rutas con escenarios de rol/anónimo/propiedad (§17.4). Ninguno se degrada a «por si acaso» porque el ejemplo simplificado o el README no lo mencione.

**Hardening posterior permitido por §16.1/§2.4/§5.2:** rate limiting/bloqueo de fuerza bruta, MFA, refresh tokens, denylist/logout servidor, rotación automatizada/gestor de secretos, OAuth2/SSO, asignación STAFF-evento (el MVP permite STAFF global solo en sus rutas operativas). No marcarlos como implementados ni exigirlos para el MVP.

**Defensa adicional, no defecto HTTP demostrado:** checks de rol en EventService.create/publish/addArtist y findPaidTicketByEvent aunque hoy la cadena ya restringe esas operaciones; validación de autenticación/active también dentro de todo helper si se prevén llamadas internas fuera del filtro; mitigar timing de login inexistente con dummy BCrypt; validación superior de longitud de inputs para evitar carga; generar secretos PowerShell con RNG criptográfico en vez del ejemplo Get-Random (`README.md:159`). La guía exige clave fuerte; no se leyeron ni evaluaron claves reales.

**BCrypt >72 en login:** el probe confirmó `matches(password72 + sufijo, hash(password72)) == true`. El registro ya impide passwords >72 bytes (`AuthServiceImpl.java:221-225`), pero LoginRequest no limita bytes y login no llama esa validación. Recomendar límite de bytes en login evita aliases por truncamiento, sin afirmar que permita adivinar los primeros 72 bytes. En HTTP, contraseña de registro multibyte >72 con <=72 caracteres termina en BusinessRuleException→409 en vez de validación de entrada400; conviene alinear el error de entrada sin relajar el límite.

**No hallazgos:** el orden by-artist después de `{eventCode}` no causa acceso distinto actualmente porque ambos matchers son permitAll y el Service filtra; la concurrencia contar-validar-guardar está expresamente aceptada por PRD Servicios §53; minimumAge omitido/null no fue aceptación silenciosa en el runtime Jackson3 que probó el padre.

## 11. Orden mínimo de corrección recomendado (sin aplicar cambios)

1. **P1:** separar dueño-o-ADMIN de dueño-o-STAFF/ADMIN y usar política correcta en cuatro operaciones; exigir USER en compra; añadir negativos de STAFF/ADMIN/sin roles.
2. **P1:** exigir exp y algoritmo HS256 al validar, con tokens negativos válidamente firmados. Luego cerrar iat/jti y política temporal.
3. **P2 obligatorio:** filtro/duplicados, rollback/registro real, upgrade Flyway/constraints, cruces faltantes de matriz y regresión500. No reemplazar pruebas por listas fijas o status404 de objetos inexistentes.
4. **P2 obligatorio:** auditoría mínima con request ID, CORS configurable y medición de autenticación; documentación de provisión privilegiada/activación legada y TLS externo.
5. Reutilizar UserService para las reglas de registro y obtener aceptación/alineación de PK surrogate. No reescribir V1–V4 ya aplicadas.
6. Integrar al informe global la suite verde actual (264/27) y el probe de configuración del padre. No condicionar hallazgos P1 a que la suite falle: sus negativos no existen. La suite verde no prueba los 5 tests históricos retirados ni la configuración runtime de producción.

## 12. Archivos creados y límites

Solo se escribieron artefactos fuera del repositorio, en `C:/Users/57321/AppData/Local/hermes/cache/scratch/`:

- Este informe `aud-seguridad.md`.
- Cuatro extracciones `.txt` de los PDF para lectura.
- `pulse-security-test-inventory.json`.
- `PulseSecurityProbe.java`, `PulseFilterProbe.java`, clases compiladas bajo `pulse-security-probe-classes/` y los dos logs de probes.

No se modificó código, tests, README, planes ni PDF del repositorio; no commits, Maven, arranque de aplicación ni acceso a BD del usuario. La verificación de git al cierre conserva el estado inicial.

**No comprobado por ejecución propia:** Maven/Testcontainers y arranque/configuración efectiva; se incorporó la evidencia leída del padre para la suite y autorización, sin duplicar probes. No se verificaron TLS desplegado, carga SQL real ni historial completo de secretos. **Riesgo restante:** permisos STAFF y validación JWT fuera del contrato comprobados por código/probes **a pesar de la suite actual verde**.
