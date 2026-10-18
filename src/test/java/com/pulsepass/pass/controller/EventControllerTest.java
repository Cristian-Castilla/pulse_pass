package com.pulsepass.pass.controller;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EVT-01..06 — operaciones de evento, con la matriz de roles del PRD §11.
 *
 *   EVT-01 POST   /api/events                          → ADMIN
 *   EVT-02 GET    /api/events/{eventCode}              → público (filtrado de borradores: Etapa 7)
 *   EVT-03 GET    /api/events/published                → público
 *   EVT-04 PATCH  /api/events/{eventCode}/publish      → ADMIN
 *   EVT-05 POST   /api/events/{eventCode}/artists/{id} → ADMIN
 *   EVT-06 GET    /api/events/by-artist                → público
 *
 * <p>Migrado de {@code @WebMvcTest} a HTTP real. Las operaciones que antes se probaban con el
 * {@code EventService} mockeado ahora pasan por el servicio real, así que cada test persiste el
 * evento que necesita (con su venue) antes de la petición.
 */
class EventControllerTest extends ControllerHttpSupport {

    @Autowired private AuthService authService;
    @Autowired private EventRepository eventRepository;

    private String bearer(String username) {
        return "Bearer " + authService.login(username, RAW_PASSWORD).accessToken();
    }

    /** Persiste el venue de referencia y un evento DRAFT en él, y devuelve el evento. */
    private Event persistDraftEvent(String eventCode) {
        Venue venue = persistReferenceVenue();
        return eventRepository.saveAndFlush(new Event(
                eventCode, "Caribbean Music Fest 2026", "Test description",
                EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDateTime.now().plusDays(30), 0, null, venue));
    }

    private String validCreateBody() {
        return """
                {
                    "eventCode": "CMF-2026",
                    "name": "Caribbean Music Fest 2026",
                    "description": "Test description",
                    "category": "MUSIC",
                    "eventDate": "2026-12-31T19:00:00",
                    "minimumAge": 0,
                    "venueCode": "VEN-SMR-01"
                }
                """;
    }

    // ------------------------------------------------------------------
    // EVT-01 — ADMIN
    // ------------------------------------------------------------------

    @Test
    void create_anonymousGets401() throws Exception {
        // PRD §11 EVT-01: ADMIN. T-SEC-002
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void create_userGets403() throws Exception {
        // T-SEC-003: "USER POST /api/events → 403, sin invocar Service"
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", bearer("andrea"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void create_adminGets201() throws Exception {
        // T-SEC-004: "ADMIN POST /api/events válido → 201"
        persistReferenceVenue();
        persistUser("sofia", "sofia@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", bearer("sofia"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"))
                .andExpect(jsonPath("$.name").value("Caribbean Music Fest 2026"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.venueCode").value("VEN-SMR-01"));
    }

    @Test
    void create_invalidRequest_returns400() throws Exception {
        // T-SEC-018: Bean Validation corre antes que el rol. Aquí se usa ADMIN para que la petición
        // no se corte en 403 y llegue al validador.
        persistUser("sofia", "sofia@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", bearer("sofia"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventCode": "",
                                    "name": "",
                                    "description": "Test description",
                                    "category": "MUSIC",
                                    "eventDate": "2026-12-31T19:00:00",
                                    "minimumAge": -5,
                                    "venueCode": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.eventCode").value("Event code is required"))
                .andExpect(jsonPath("$.details.name").value("Name is required"))
                .andExpect(jsonPath("$.details.venueCode").value("Venue code is required"))
                .andExpect(jsonPath("$.details.minimumAge")
                        .value("Minimum age must be zero or greater"));
    }

    @Test
    void create_descriptionExceeds500Chars_returns400() throws Exception {
        persistUser("sofia", "sofia@example.com", Role.ADMIN);
        String longDescription = "a".repeat(501);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", bearer("sofia"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventCode": "CMF-2026",
                                    "name": "Caribbean Music Fest 2026",
                                    "description": "%s",
                                    "category": "MUSIC",
                                    "eventDate": "2026-12-31T19:00:00",
                                    "minimumAge": 0,
                                    "venueCode": "VEN-SMR-01"
                                }
                                """.formatted(longDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.description")
                        .value("Description must not exceed 500 characters"));
    }

    // ------------------------------------------------------------------
    // EVT-02 / EVT-03 / EVT-06 — públicos
    // ------------------------------------------------------------------

    @Test
    void findByCode_existingEvent_returns200() throws Exception {
        // PRD §11 EVT-02: "Público solo si publicado". El evento es DRAFT en este test; el filtrado
        // de borradores es de la Etapa 7, así que hoy la ruta es alcanzable por un anónimo.
        persistDraftEvent("CMF-2026");

        mockMvc.perform(get("/api/events/{eventCode}", "CMF-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"))
                .andExpect(jsonPath("$.venueName").value("Marina Convention Center"));
    }

    @Test
    void findByCode_nonExisting_returns404() throws Exception {
        // 404 de negocio: si la ruta fuera protegida, un anónimo recibiría 401
        mockMvc.perform(get("/api/events/{eventCode}", "XXX-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Event code doesn't exist: XXX-999"));
    }

    @Test
    void findPublishedEvents_returns200() throws Exception {
        // T-SEC-001: "Anónimo GET /api/events/published → 200"
        Venue venue = persistReferenceVenue();
        eventRepository.saveAndFlush(new Event(
                "CMF-2026", "Caribbean Music Fest 2026", "Test description",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDateTime.now().plusDays(30), 0, null, venue));

        mockMvc.perform(get("/api/events/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventCode").value("CMF-2026"));
    }

    @Test
    void findByArtist_returns200() throws Exception {
        // PRD §11 EVT-06: "Público únicamente para eventos publicados"
        Venue venue = persistReferenceVenue();
        Event event = eventRepository.saveAndFlush(new Event(
                "CMF-2026", "Caribbean Music Fest 2026", "Test description",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDateTime.now().plusDays(30), 0, null, venue));
        event.addArtist(persistArtist("Solar Beat"));
        eventRepository.saveAndFlush(event);

        mockMvc.perform(get("/api/events/by-artist").param("stageName", "Solar Beat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventCode").value("CMF-2026"));
    }

    // ------------------------------------------------------------------
    // EVT-04 / EVT-05 — ADMIN
    // ------------------------------------------------------------------

    @Test
    void publish_adminGets200() throws Exception {
        // T-SEC-014: "ADMIN publica evento → 200"
        persistDraftEvent("CMF-2026");
        persistUser("sofia", "sofia@example.com", Role.ADMIN);

        mockMvc.perform(patch("/api/events/{eventCode}/publish", "CMF-2026")
                        .header("Authorization", bearer("sofia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void publish_userGets403() throws Exception {
        // UC-SEC-08: "USER intenta publicar CMF-2026 → recibe 403 y no se ejecuta la publicación"
        persistDraftEvent("CMF-2026");
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(patch("/api/events/{eventCode}/publish", "CMF-2026")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("No tiene permisos para realizar esta operación"));
    }

    @Test
    void publish_invalidEvent_returns409() throws Exception {
        // BR-EVENT-007: "Solo se puede publicar un evento en estado DRAFT".
        // Se crea ya PUBLISHED para que el servicio real rechace la transición.
        Venue venue = persistReferenceVenue();
        eventRepository.saveAndFlush(new Event(
                "INVALID-001", "Already published", "Test description",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDateTime.now().plusDays(30), 0, null, venue));
        persistUser("sofia", "sofia@example.com", Role.ADMIN);

        mockMvc.perform(patch("/api/events/{eventCode}/publish", "INVALID-001")
                        .header("Authorization", bearer("sofia")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Only DRAFT events can be published, but event INVALID-001 is PUBLISHED"));
    }

    @Test
    void addArtist_adminGets200() throws Exception {
        // PRD §11 EVT-05: ADMIN
        persistDraftEvent("CMF-2026");
        Artist artist = persistArtist("Solar Beat");
        persistUser("sofia", "sofia@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/events/{eventCode}/artists/{artistId}", "CMF-2026", artist.getId())
                        .header("Authorization", bearer("sofia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"))
                .andExpect(jsonPath("$.artists.length()").value(1));
    }

    @Test
    void addArtist_userGets403() throws Exception {
        persistDraftEvent("CMF-2026");
        Artist artist = persistArtist("Solar Beat");
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(post("/api/events/{eventCode}/artists/{artistId}", "CMF-2026", artist.getId())
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isForbidden());
    }
}
