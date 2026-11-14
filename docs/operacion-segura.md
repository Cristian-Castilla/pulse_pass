# Operación segura de PulsePass

Este procedimiento es manual, exclusivo de un operador autorizado y no lo ejecuta el arranque de la
aplicación. No crea cuentas privilegiadas predeterminadas, no modifica contraseñas en masa y no se
aplica automáticamente sobre una base existente. La auditoría y sus pruebas usan PostgreSQL efímero.

## 1. Preparar una actualización

1. Confirma el destino y toma un respaldo recuperable antes de actualizar una base persistente.
2. Conserva V1–V4 intactas. Flyway aplica V5 sobre V4 y preserva las asignaciones existentes;
   `ddl-auto=validate` comprueba el esquema resultante. No usar `repair`, `clean` ni recrear tablas
   para ocultar un checksum incompatible: detén el despliegue y compara su historial.
3. Configura DB_URL/DB_USER/DB_PASSWORD y una clave JWT aleatoria de al menos 32 bytes vía entorno
   o `.env.properties` ignorado por Git. No copies las claves ficticias del perfil de pruebas.
4. El rol inicial del registro público sigue siendo USER. ADMIN no implica USER: un administrador
   que también necesite comprar debe recibir ambos roles explícitamente.

## 2. Otorgar o retirar STAFF/ADMIN

Primero registra a la persona por `POST /api/users` (contraseña elegida por ella, por HTTPS). Verifica
por un canal independiente que la identidad y el permiso solicitado son legítimos. En una consola
PostgreSQL restringida al operador, sustituye **solo** el ID verificado y el rol aprobado:

```sql
BEGIN;
SELECT id, username, active FROM users WHERE id = 123 FOR UPDATE;
-- Debe devolver exactamente la cuenta verificada y debe tener credencial aprovisionada.
INSERT INTO user_roles (user_id, role)
SELECT u.id, 'STAFF' FROM users u
JOIN user_credentials c ON c.user_id = u.id
WHERE u.id = 123 AND u.active = true
ON CONFLICT (user_id, role) DO NOTHING;
COMMIT;
```

Si la consulta no devuelve la cuenta esperada, ejecuta ROLLBACK, no continúes. Comprueba el resultado
con `SELECT user_id, role FROM user_roles WHERE user_id = 123;`. No otorgues ADMIN por conveniencia.
Para retirar el permiso, usa una transacción y `DELETE FROM user_roles WHERE user_id = 123 AND
role = 'STAFF';`, y vuelve a consultar. Los permisos/estado se releen en la siguiente solicitud;
no hay que esperar a que expire el mismo JWT. El registro HTTP no acepta roles privilegiados.

## 3. Activar una cuenta legada sin contraseña predeterminada

1. Verifica al titular y el ID. No se crea credencial a todas las cuentas legadas durante Flyway.
2. El titular introduce una nueva contraseña mediante un canal administrativo seguro. Nunca la
   incluyas como argumento de shell, SQL, captura, ticket, chat, archivo versionado o log.
3. Genera el hash offline con el mismo BCrypt que usa la aplicación. El pequeño programa
   `tools/PasswordHash.java` lee la contraseña **enmascarada**, pide confirmación y no toca la BD.
   Requiere Java 21 y las dependencias existentes del proyecto, sin instalar bibliotecas nuevas.

Desde PowerShell en la raíz del proyecto:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.11'
$maven = 'C:/Users/57321/apache-maven-3.9.16'
& "$env:JAVA_HOME/bin/java.exe" -classpath "$maven/boot/plexus-classworlds-2.11.0.jar" "-Dclassworlds.conf=$maven/bin/m2.conf" "-Dmaven.home=$maven" "-Dmaven.multiModuleProjectDirectory=$PWD" org.codehaus.plexus.classworlds.launcher.Launcher dependency:build-classpath '-Dmdep.outputFile=target/operator-classpath.txt'
$cp = (Get-Content -Raw target/operator-classpath.txt).Trim()
& "$env:JAVA_HOME/bin/java.exe" --class-path $cp tools/PasswordHash.java
```

La contraseña debe tener al menos 12 caracteres y como máximo 72 bytes UTF-8, no solo 72 caracteres.
El hash mostrado también se trata como sensible: no lo subas al repositorio ni lo compartas. El
programa no recibe contraseñas por argumentos o tuberías. `--self-test` usa únicamente datos ficticios.

4. En el cliente administrativo de BD usa **parámetros enlazados**, no construyas SQL concatenando
   la contraseña/hash. Dentro de una transacción verifica/bloquea la fila de usuario y ejecuta:

```sql
-- :user_id y :bcrypt_hash son parámetros del cliente administrativo, no texto para concatenar.
INSERT INTO user_credentials (user_id, password_hash, password_changed_at)
VALUES (:user_id, :bcrypt_hash, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO UPDATE
SET password_hash = EXCLUDED.password_hash, password_changed_at = EXCLUDED.password_changed_at;
INSERT INTO user_roles (user_id, role) VALUES (:user_id, 'USER')
ON CONFLICT (user_id, role) DO NOTHING;
```

5. Activa `users.active` solo si la desactivación ya fue autorizadamente resuelta; no borres roles
   previos ni promociones por defecto. Haz COMMIT y verifica que existen credencial y rol mediante
   consultas que **no seleccionen password_hash**. Prueba login del titular y una operación propia.
   La transacción debe revertirse completa ante cualquier fallo. Desactiva logging de parámetros
   en ese cliente y elimina de su memoria/portapapeles el hash después de utilizarlo.

No se ejecutó este procedimiento sobre cuentas reales. Debe quedar registro administrativo de
**operador, ID objetivo, permiso y motivo**, nunca de contraseñas, hashes o JWT.

## 4. CORS, request ID y JWT

- `PULSEPASS_CORS_ALLOWED_ORIGINS=https://app.example.com,http://localhost:3000` permite solo esos
  orígenes exactos. Por defecto la lista está vacía: no se autorizan clientes de otros orígenes.
  No se permite wildcard, credenciales en URL ni rutas. No se habilitan cookies de autenticación.
- El preflight aprobado pasa antes de exigir Bearer; no autoriza por sí mismo la operación real.
- Cada respuesta recibe `X-Request-ID` generado por el servidor. Los logs de éxito/fallo de login
  y denegación incluyen ese mismo ID sin identidad de login, contraseña, hash ni token. El valor
  enviado por el cliente se ignora; el MDC se limpia/restaura al salir del request.
- Solo se recibe Bearer en un único Authorization. Dos headers o valores combinados no autentican.
- El JWT recibido exige HS256, firma/issuer correctos, sub positivo, iat, exp y jti no vacío.
  Se toleran hasta 30 segundos de iat futuro por deriva de reloj, **sin ampliar la expiración**.
- Las mediciones SQL/tiempo del test AuthenticationLoadTest son mediciones locales de un request,
  no una prueba de carga, un SLA ni evidencia de capacidad de producción.

## 5. HTTPS antes de exponerlo a terceros

MockMvc/localhost no demuestran HTTPS de un despliegue. Antes de publicar, termina TLS con certificado
válido en un proxy/balanceador controlado y bloquea el acceso externo directo al puerto HTTP de la
aplicación (loopback o red privada). No aceptes forwarded headers desde redes no confiables.

Comprueba desde fuera de esa red, sustituyendo el dominio real:

```bash
curl --fail --show-error https://api.example.com/api/events/published
curl --head http://api.example.com/api/events/published
```

La primera debe validar el certificado **sin `-k`**; la segunda debe redirigir a HTTPS o ser rechazada.
Prueba también login/errores/Authorization a través del proxy y que el puerto interno no sea accesible.
No envíes contraseñas ni tokens a un HTTP público. No hay dominio/certificado aportado en esta tarea:
**la aplicación puede verificarse localmente, pero ese control externo continúa siendo un gate de despliegue**.

## 6. Verificación en este Windows / Git Bash

Si `docker info` funciona pero Testcontainers falla con `NpipeSocketClientProviderStrategy` y un
400 vacío, el cliente Java puede estar usando `docker_engine` mientras Docker Desktop usa
`dockerDesktopLinuxEngine`. Se comprobó este caso mediante una llamada real a DockerClientFactory.
Selecciona explícitamente el mismo endpoint **local** para la ejecución de pruebas, sin modificar
el código de la aplicación ni reiniciar una base persistente:

```bash
export JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
export DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
export TESTCONTAINERS_DOCKER_CLIENT_STRATEGY='org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy'
"$JAVA_HOME/bin/java" -Xmx256m -XX:ActiveProcessorCount=2 \
  -classpath 'C:/Users/57321/apache-maven-3.9.16/boot/plexus-classworlds-2.11.0.jar' \
  -Dclassworlds.conf=C:/Users/57321/apache-maven-3.9.16/bin/m2.conf \
  -Dmaven.home=C:/Users/57321/apache-maven-3.9.16 \
  -Dmaven.multiModuleProjectDirectory=C:/Users/57321/Desktop/pulse_pass \
  org.codehaus.plexus.classworlds.launcher.Launcher clean test \
  '-DargLine=-Xmx384m -XX:ActiveProcessorCount=2'
```

Este comando es específico de las rutas comprobadas en este equipo. En otros entornos basta
`mvn clean test` con Java21 y Docker disponibles. El presupuesto de memoria solo limita las JVM
de pruebas en esta máquina; no cambia configuración ni límites de la aplicación desplegada.
