# Pruebas E2E Manuales — PulsePass (Fase 5)

Este directorio contiene la suite de pruebas End-to-End (E2E) para la plataforma PulsePass, correspondiente a la Fase 5 del proyecto. El objetivo es validar de forma manual, a través de un cliente HTTP real (Postman), la integración de las capas de Persistencia (Fase 1), Servicios (Fase 2), Controladores REST (Fase 3) y Seguridad (Fase 4).

## 🎯 Alcance
*   **22 rutas cubiertas:** 20 endpoints de negocio + 2 de autenticación.
*   **Roles evaluados:** ANONYMOUS, USER, STAFF, ADMIN.
*   **Pruebas de Seguridad:** Validación de JWT, ataques IDOR (acceso horizontal), y reglas de propiedad.
*   **Pruebas de Negocio:** Flujo completo de creación/publicación de eventos, compra, consulta, uso y cancelación de tickets.

## 📂 Estructura del directorio
*   `postman/`: Contiene la colección exportable (`PulsePass-Manual-E2E.postman_collection.json`) y el entorno (`PulsePass-local.postman_environment.json`).
*   `fixtures/`: Contiene el script SQL (`seed-e2e.sql`) para poblar la base de datos con datos controlados (usuarios ADMIN/STAFF, venue base, etc.).
*   `checklist-e2e-manual.md`: Guía paso a paso de cómo ejecutar cada caso de prueba.
*   `registro-ejecucion-manual.md`: Bitácora con los resultados reales obtenidos durante la ejecución de las pruebas.
*   `README.md`: Este archivo.

## 🚀 Preparación del entorno

### 1. Base de datos y Backend
1.  Asegúrate de tener PostgreSQL corriendo y el esquema creado (vía Flyway).
2.  Ejecuta el script `fixtures/seed-e2e.sql` en tu base de datos local. Esto creará los usuarios `e2e_admin` y `e2e_staff` con sus contraseñas hasheadas (BCrypt), además de los datos base.
3.  Levanta la aplicación Spring Boot (ej. `./mvnw spring-boot:run`). El servidor debe estar escuchando en `http://localhost:8080`.

### 2. Configuración de Postman
1.  Abre Postman.
2.  Importa la Colección (`postman/PulsePass-Manual-E2E.postman_collection.json`).
3.  Importa el Entorno (`postman/PulsePass-local.postman_environment.json`).
4.  Selecciona el entorno "PulsePass-local" en la esquina superior derecha de Postman.
5.  Revisa las variables del entorno. Las contraseñas de los usuarios están definidas allí (ej. `adminPassword`, `staffPassword`). Asegúrate de que coincidan con las que usaste en el seed SQL.

## ⚠️ Reglas de Ejecución (Importante)
*   **Ejecución 100% Manual:** No uses el *Collection Runner* ni scripts de automatización. Abre cada petición, presiona **Send**, e inspecciona los resultados visualmente.
*   **Cero Scripts:** La colección no contiene código en las pestañas *Pre-request* ni *Tests*. La validación depende exclusivamente del evaluador humano.
*   **Encadenamiento de Tokens:** Las peticiones que requieren autenticación usan variables (`{{adminToken}}`, `{{userAToken}}`, etc.). Debes hacer login manualmente, copiar el `accessToken` de la respuesta y pegarlo en la variable correspondiente en la pestaña *Environment* de Postman.
*   **Confidencialidad:** Nunca subas tokens reales ni contraseñas al repositorio. Las credenciales en el seed son exclusivamente para el entorno local de pruebas.

## 📝 Flujo de Ejecución
1.  Lee el `checklist-e2e-manual.md` para saber qué petición hacer a continuación.
2.  Ejecuta la petición en Postman.
3.  Anota el resultado real en el `registro-ejecucion-manual.md`.
4.  Si un caso falla por un prerrequisito no cumplido, márcalo como `BLOCKED` y continúa.