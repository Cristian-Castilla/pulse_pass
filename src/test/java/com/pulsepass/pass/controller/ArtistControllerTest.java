package com.pulsepass.pass.controller;

import com.pulsepass.pass.domain.Artist;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ART-01..03 — endpoints públicos de artistas.
 *
 * PRD de Seguridad §11: los tres endpoints de artista son "Público".
 *
 * <p>Migrado de {@code @WebMvcTest} a HTTP real. La semilla V2 inserta 5 artistas (Solar Beat,
 * Neon Waves, Caribbean Sound, Ocean Drive y Digital Pulse), así que las aserciones usan esos
 * nombres en vez de fixtures locales: es el estado real de la base.
 */
class ArtistControllerTest extends ControllerHttpSupport {

    @Test
    void findById_existingArtist_returns200() throws Exception {
        // No se usa la semilla V2: este test crea su propio artista y aserta contra él. La semilla
        // inserta 5 artistas, así que contar con ella haría el test dependiente del orden de
        // ejecución y del estado que dejen otros tests.
        Artist artist = persistArtist("Solar Beat");

        mockMvc.perform(get("/api/artists/{id}", artist.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(artist.getId()))
                .andExpect(jsonPath("$.stageName").value("Solar Beat"))
                .andExpect(jsonPath("$.country").value("Colombia"))
                .andExpect(jsonPath("$.genre").value("Electronic"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void findById_nonExistingId_returns404() throws Exception {
        // 404 de negocio: si la ruta fuera protegida, un anónimo recibiría 401
        mockMvc.perform(get("/api/artists/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Artists doesnt exist: 999"));
    }

    @Test
    void findByStageName_existingArtist_returns200() throws Exception {
        // Se crea el artista propio: clearState() borra la semilla V2 en @BeforeEach, así que
        // pedir "Solar Beat" devolvería 404.
        persistArtist("Solar Beat");

        mockMvc.perform(get("/api/artists/by-stage-name").param("stageName", "Solar Beat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageName").value("Solar Beat"));
    }

    @Test
    void findByStageName_nonExisting_returns404() throws Exception {
        mockMvc.perform(get("/api/artists/by-stage-name").param("stageName", "Nadie"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Artist with stage name Nadie doesn't exist."));
    }

    @Test
    void findActiveArtists_returns200() throws Exception {
        // Se crea un artista propio y se verifica que aparece en la lista de activos. No se cuenta
        // contra la semilla V2: este test limpia la tabla en @BeforeEach, así que la lista es la
        // que el propio test creó.
        Artist solarBeat = persistArtist("Solar Beat");
        Artist neonWaves = persistArtist("Neon Waves");

        mockMvc.perform(get("/api/artists/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // BR-ARTIST-002 + orden del repositorio (ORDER BY stageName ASC)
                .andExpect(jsonPath("$[0].stageName").value("Neon Waves"))
                .andExpect(jsonPath("$[1].stageName").value("Solar Beat"));
    }

    @Test
    void findActiveArtists_excludesInactiveArtist() throws Exception {
        // BR-ARTIST-002: findActiveArtists() solo retorna artistas activos.
        // Artist no expone setter (inmutabilidad deliberada), así que se crea ya inactivo.
        persistArtist("Active Artist");
        artistRepository.saveAndFlush(new Artist("Retired Artist", "Colombia", "Rock", false));

        mockMvc.perform(get("/api/artists/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stageName").value("Active Artist"));
    }
}
