# Informe de cierre — Correcciones de PulsePass

## Dictamen

**CORRECCIONES DEL REPOSITORIO COMPLETADAS Y VERIFICADAS.** El proyecto queda listo para revisión y
entrega académica en el entorno local probado. Esto no certifica un despliegue de producción ni
una base persistente que no se ha inspeccionado.

Se corrigieron los hallazgos A01–A19 de `Auditoria_Completa_PulsePass.md` en código, configuración,
pruebas o documentación, según su naturaleza. El diagnóstico original se conserva como histórico.
Los controles externos de HTTPS y la aplicación de V5 sobre la base personal siguen siendo pasos
de despliegue deliberadamente no ejecutados, no pruebas locales aprobadas por suposición.

- Rama comprobada: `feature/rebuild-pulsepass`.
- HEAD base y final: `5c9056f385f0f459f39b809029718db2b8f0ee98`.
- Cambios presentes en el working tree, **sin staging, commit ni push**.
- PRD y guías no editados. V1–V4 conservan sus bytes originales; la evolución adicional está en V5.

## 1. Evidencia final ejecutada

```text
Comando: clean test mediante el launcher Maven por classpath (Windows/MSYS)
Tests run: 680, Failures: 0, Errors: 0, Skipped: 0
Suites: 49
BUILD SUCCESS
Total time: 14:17 min
Exit code: 0
```

**No es una suma de ejecuciones parciales:** los 680 casos pertenecen a una única ejecución global
nueva sobre este repositorio, después de integrar todas las correcciones. Se contrastaron los XML
Surefire con las fuentes: **49 clases actuales encontradas, 49 ejecutadas, ninguna ausente**.
Los escenarios parametrizados cuentan como ejecuciones individuales; no se confunden con el número
de métodos anotados. Un contenedor PostgreSQL de pruebas sirvió a la suite global.

| Grupo por paquete de prueba | Suites | Pruebas | Fallos / errores / omitidas |
|---|---:|---:|---|
| Persistencia y configuración | 13 | 73 | 0 / 0 / 0 |
| HTTP | 13 | 97 | 0 / 0 / 0 |
| Seguridad | 13 | 381 | 0 / 0 / 0 |
| Servicios | 10 | 129 | 0 / 0 / 0 |
| **Total** | **49** | **680** | **0 / 0 / 0** |

También se verificó:

- `git diff --check`: sin errores de whitespace.
- Índice Git sin archivos preparados y HEAD sin cambios: no hubo commit.
- Hashes de fuentes/configuración/migraciones/PRD antes y después del run: **sin cambios durante la ejecución**.
- Comparación con el baseline inicial: **PRD y V1–V4 sin cambios**.
- Herramienta offline BCrypt: `PASSWORD_HASH_SELF_TEST_OK`, exit 0; solo datos ficticios.

### Incidencia encontrada y resuelta durante la integración

El primer `clean test` ejecutó 680 casos con un error en `SecurityCredentialsTest`: Hibernate fallaba
al proyectar la identidad compuesta en una consulta `exists` derivada. No se eliminó la prueba ni
se volvió a una PK técnica. Se mantuvo el contrato booleano y se sustituyeron **ambas** variantes
(username/email ignore-case) por JPQL escalar `COUNT > 0`. Se afirman resultados positivos y negativos.

La prueba focalizada posterior pasó **10 casos, 0 fallos/errores/omitidas**, y después se ejecutó la
suite global verde de arriba. El log fallido inicial se conserva, pero no se usa como evidencia de cierre.

## 2. Cierre trazable de los hallazgos

| ID | Corrección aplicada | Evidencia de la suite final |
|---|---|---|
| A01 | datasource/JPA/Flyway bajo `spring`; configuración principal heredada por perfil test; import local de .env solo fuera de test | ApplicationConfigurationTest y ApplicationRuntimeConfigurationTest: validate efectivo, open-in-view=false y URL igual al contenedor efímero |
| A02 | Separados propietario/ADMIN y propietario/STAFF/ADMIN; STAFF operativo no obtiene acceso a datos/listados/cancelaciones ajenas | AuthorizationServiceImplTest, OwnershipAntiIdorTest y matriz HTTP |
| A03 | Compra exige USER y propietario activo; cancelación exige USER propietario o ADMIN; no hay herencia implícita de USER | Matriz con roles individuales/combinados, sin roles, dueño/tercero y cambios de rol/activo con el mismo JWT |
| A04 | Se exige exp futuro y algoritmo recibido HS256, además de verificar firma/issuer/sub | JwtServiceTest: firmas válidas sin exp o con HS384/HS512 se rechazan |
| A05 | iat y jti obligatorios; exp>iat; sub positivo y TTL/emisor válidos; tolerancia iat30s sin ampliar exp | Casos parametrizados de JwtServiceTest |
| A06 | Query ausente400, método405 y media415 conservan status; excepción inesperada500 genérica sin detalles privados | HttpErrorContractTest y SecurityHttpContractSliceTest, incluidas las familias de parámetros y entrada malformada |
| A07 | Cinco suites aisladas WebMvcTest/MockitoBean con filtros e interacción verificada; matriz ejecuta requests reales, no cuenta una lista fija | 228 escenarios de AuthorizationMatrixTest; snapshots comprueban ausencia de efectos en denegaciones; prueba500 genuina |
| A08 | Tests de catálogo, historial y validación efectiva; upgrades poblados V3→V5 y V4→V5, reejecución sin pendientes | FlywayMigrationTest y FlywayMigrationOnExistingSchemaTest: preservación de IDs de negocio/relaciones/valores/fecha/credenciales y roles |
| A09 | CHECK/FK/PK de PostgreSQL y relaciones con flush/clear/readback; SQLSTATE y nombres de constraints afirmados | PersistenceConstraintsTest, SecurityCredentialsTest, UserProfileTest y repositories; capacidad0/-1, precio negativo/cero, enums/rol desconocido no nulo |
| A10 | Consultas con distractores, orden cronológico, límites temporales, DISTINCT, multiplicidad y todos los estados no PAID | EventRepositoryTest y TicketRepositoryTest; tres artistas/par no duplicado y artista en varios eventos |
| A11 | addArtist probado para éxito, duplicado, estados finales y recursos inexistentes; sin mutación/guardado al rechazar | EventServiceRulesTest |
| A12 | Calculador real de precios probado para GENERAL/STUDENT/VIP/BACKSTAGE y BigDecimal | PriceCalculatorTest y capturas de compra con precio real |
| A13 | Transiciones y asociaciones afirmadas sobre entidades reales/capturas, no solo DTOs prefabricados | EventServiceRulesTest y TicketServiceRulesTest: DRAFT/PUBLISHED/PAID/SOLD_OUT/CANCELLED/USED y ramas de negocio |
| A14 | AuthService orquesta UserService y mantiene una transacción exterior para usuario/perfil/credencial/USER | AuthRegistrationIntegrationTest sin transacción exterior de test: éxito BCrypt y fallo tardío con escrituras ya flushed seguido de lectura en otra transacción |
| A15 | Solo un Authorization Bearer no ambiguo; duplicados, valores combinados, schemes inválidos y otras fuentes no autentican; contexto existente protegido | JwtAuthenticationFilterTest y AuthenticationLookupTest |
| A16 | CORS exacto/restrictivo parametrizado, request ID generado por servidor y logs seguros de login/denegación; medición SQL real | CorsPropertiesTest, RequestIdFilterTest, SecurityAuditLoggingIntegrationTest y AuthenticationLoadTest |
| A17 | Nueva V5 alinea la PK de roles a (user_id,role); IdClass/repository adaptados; ambas consultas exists corregidas | Catálogo y upgrade V4 con roles existentes; lookup/borrado independiente por clave compuesta y existencia username/email |
| A18 | README y matriz actualizados; provisión privilegiada/activación legada/HTTPS y comando Windows documentados | docs/operacion-segura.md y tools/PasswordHash.java; proceso administrativo no ejecutado sobre cuentas reales |
| A19 | Contrato `findPaidTicketsByEvent` consistente entre interfaz, implementación y caller HTTP | Compilación limpia, TicketServiceRulesTest y EventControllerSliceTest |

## 3. Riesgos preventivos atendidos y límites conservados

- **Cascada de credenciales:** PERSIST/MERGE no incluye REMOVE hacia el usuario. Se probó borrar la
  credencial y conservar usuario/perfil mediante flush/clear y nueva lectura.
- **Código de ticket:** conserva el UUID completo dentro de VARCHAR(50); captura y matriz verifican
  su formato. La restricción UNIQUE sigue siendo la garantía final de integridad, no se afirma
  imposibilidad matemática de colisión.
- **BCrypt:** login >72 bytes UTF-8 se rechaza con401 genérico; registro inválido se clasifica400.
  Hay prueba unitaria explícita de enabled=false sin comprobar contraseña ni emitir JWT.
- **Fixtures:** se reforzó aislamiento transaccional y se usan fechas relativas para la edad de compra.
  Los catálogos/migraciones tienen pruebas específicas, no asunciones por inyección de un bean.
- **Consultas secundarias:** añadidas pruebas de búsquedas de usuario/artista, listados/mapping/orden,
  visibilidad y vacíos; no se añadieron endpoints, dependencias ni abstracciones de precios innecesarias.
- **Índice potencialmente redundante:** no se eliminó un índice histórico por intuición, sin EXPLAIN
  o evidencia de costo que justifique otra migración. No era un defecto funcional demostrado.
- Rate limiting, MFA, refresh, denylist, rotación automática y asignación STAFF-evento permanecen fuera
  del MVP. STAFF global aplica a inspección/ingreso, no a datos personales/cancelaciones ajenas.

## 4. Medición de autenticación y advertencias

En la ejecución global final se limpiaron cachés del contexto de persistencia entre escenarios:

```text
AUTH_LOAD roles=1 statements=4 elapsedMicros=124643
AUTH_LOAD roles=3 statements=4 elapsedMicros=50483
```

Ambos escenarios ejecutaron **4 sentencias**, sin crecimiento por cada rol. Son mediciones locales
individuales, **no** una prueba de carga ni una promesa de latencia/SLA de producción.

La JVM mostró advertencias no fatales de carga dinámica del agente Byte Buddy usado por Mockito:
se ejecutaron todos los tests sin omisiones. No se ocultaron esos warnings añadiendo opciones para
silenciarlos. Las violaciones SQL esperadas en pruebas de constraints tampoco se confundieron con
fallos del build.

Docker Desktop en este equipo requería el pipe Linux explícito para el cliente Java. La base de
pruebas admite hasta3min de readiness en el host lento, sin demorar una base ya lista. La selección
de pipe y límites de memoria son del comando local, no de la aplicación productiva.

## 5. Archivos y evidencias de entrega

- Este cierre: `Correcciones_Auditoria_PulsePass.md`.
- Diagnóstico histórico conservado: `Auditoria_Completa_PulsePass.md`.
- Operación/provisión/activación/HTTPS/comando de verificación: `docs/operacion-segura.md`.
- Nueva migración: `src/main/resources/db/migration/V5__align_user_role_primary_key.sql`.
- Hash offline con entrada enmascarada: `tools/PasswordHash.java`.
- Reportes recién generados: `target/surefire-reports/TEST-*.xml`.
- Log completo final: `C:/Users/57321/AppData/Local/hermes/cache/scratch/pulsepass-final-clean-test.log`.
- Reconciliación programática y hashes: `C:/Users/57321/AppData/Local/hermes/cache/scratch/pulsepass-final-verification.json`.
- Primera ejecución fallida preservada: `C:/Users/57321/AppData/Local/hermes/cache/scratch/pulsepass-final-clean-test-initial.log`.
- Prueba focalizada del error corregido: `C:/Users/57321/AppData/Local/hermes/cache/scratch/pulsepass-role-query-fix-test.log`.

Para repetir la suite en este Windows, usar el comando exacto de `docs/operacion-segura.md` §6.
En un entorno con Maven/Java21/Docker configurados normalmente, el objetivo es `mvn clean test`.
No se añadieron includes/excludes para obtener un verde parcial ni se deshabilitaron filtros.

## 6. Qué no se ejecutó y qué falta antes de despliegue

1. **Base persistente del usuario:** no se conectó ni se migró. Antes de arrancar esta versión sobre
   ella, tomar respaldo recuperable y comprobar su historial Flyway. V5 se aplicará al desplegar;
   ante checksums incompatibles detenerse, no hacer repair/reset ni editar V1–V4.
2. **HTTPS externo:** requiere dominio, certificado y control del proxy/red no aportados. Verificar
   certificado real sin `-k` y bloquear acceso externo directo al HTTP interno antes de publicar.
3. No se certifican carga concurrente, pentest externo ni auditoría exhaustiva de dependencias o
   historial de secretos. No se abrieron archivos locales de credenciales.
4. La provisión de STAFF/ADMIN y activación de cuentas reales es manual, autorizada y auditada;
   no se ejecutó sobre personas reales ni se crean contraseñas/roles privilegiados por defecto.
5. **No hay commit ni push.** El usuario conserva la decisión sobre qué archivos incluir y el plan
   exacto de commit antes de aprobarlo. Los documentos/archivos ajenos a esta corrección no se borraron.

**Cierre:** repositorio corregido y suite global verde; los controles de despliegue anteriores siguen
explícitamente separados de la aceptación local. No quedan pruebas de esta suite sin ejecutar.
