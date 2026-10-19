package com.pulsepass.pass.controller;

import com.pulsepass.pass.domain.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VEN-01 / VEN-02 — endpoints públicos de venues.
 *
 * PRD de Seguridad §11: "GET /api/venues/{code} — Público" y "GET /api/venues/active — Público".
 * PRD §14.3 adv. 3: "La ruta GET /api/venues/active es pública y queda incluida en /api/venues/**;
 * verificarla con una prueba HTTP explícita".
 *
 * <p>Migrado de {@code @WebMvcTest} a HTTP real con la cadena de filtros: las rutas son públicas,
 * así que un anónimo las alcanza sin token y el servicio real devuelve lo persistido.
 */
class VenueControllerTest extends ControllerHttpSupport {

    @Test
    void findByCode_existingVenue_returns200() throws Exception {
        Venue venue = persistReferenceVenue();

        mockMvc.perform(get("/api/venues/{code}", venue.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(venue.getId()))
                .andExpect(jsonPath("$.code").value("VEN-SMR-01"))
                .andExpect(jsonPath("$.name").value("Marina Convention Center"))
                .andExpect(jsonPath("$.city").value("Santa Marta"))
                .andExpect(jsonPath("$.capacity").value(5000))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void findByCode_nonExisting_returns404() throws Exception {
        // El 404 es de negocio (ResourceNotFoundException) y no de seguridad: si la ruta estuviera
        // mal declarada como protegida, un anónimo recibiría 401 en vez de 404.
        mockMvc.perform(get("/api/venues/{code}", "XXX-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Venue not found: XXX-999"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void findActiveVenues_returns200() throws Exception {
        persistReferenceVenue();
        venueRepository.saveAndFlush(new Venue(
                "VEN-SMR-02", "Convention Center", "Santa Marta", "Cra 2 #20-30", 3000L, true));

        // BR-VENUE-001: el orden lo da la consulta del repositorio (ORDER BY name ASC), no el orden
        // de inserción. "Convention Center" va antes que "Marina Convention Center".
        mockMvc.perform(get("/api/venues/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("VEN-SMR-02"))
                .andExpect(jsonPath("$[1].code").value("VEN-SMR-01"));
    }

    @Test
    void findActiveVenues_inactiveVenueIsNotReturned() throws Exception {
        // BR-VENUE-002: findActiveVenues() solo retorna venues con active = true.
        // Venue no expone setter (inmutabilidad deliberada), así que se crea ya inactivo.
        persistReferenceVenue();
        venueRepository.saveAndFlush(new Venue(
                "VEN-SMR-03", "Closed Venue", "Santa Marta", "Cra 3 #30-40", 1000L, false));

        mockMvc.perform(get("/api/venues/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("VEN-SMR-01"));
    }

    @Test
    void findByCode_unexpectedException_returns500() throws Exception {
        // PRD §13: "Error inesperado → 500 Internal Server Error → Handler previo; sin stack trace".
        // Se provoca un fallo real contra la base: un id de venue que rompe la consulta.
        mockMvc.perform(get("/api/venues/{code}", "ERR-500")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
