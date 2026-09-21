# PulsePass

Plataforma de eventos, artistas y entradas para descubrir conciertos, festivales, conferencias y eventos culturales.

Caso de estudio académico para capa de persistencia.

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
    url: ${DB_URL:jdbc:postgresql://localhost:5432/deepblue}
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
