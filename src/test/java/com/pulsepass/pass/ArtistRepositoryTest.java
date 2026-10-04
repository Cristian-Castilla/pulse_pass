package com.pulsepass.pass;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.repository.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica el catálogo sembrado por V2 y la restricción UNIQUE en stage_name.
 *
 * PRD: "stageName UNIQUE", "stageName NOT NULL"
 * Query Method: findByStageName
 */
class ArtistRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private ArtistRepository artistRepository;

    @Test
    void shouldFindSeededArtistByStageName() {
        // V2 debe haber sembrado "Solar Beat"
        assertThat(artistRepository.findByStageName("Solar Beat")).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenStageNameDoesNotExist() {
        assertThat(artistRepository.findByStageName("Nonexistent Artist")).isEmpty();
    }

    @Test
    void shouldEnforceUniqueStageNameConstraintOnSaveAndFlush() {
        Artist original = artistRepository.saveAndFlush(
                new Artist("Unique Stage Test", "Colombia", "Rock", true));

        Artist duplicate = new Artist("Unique Stage Test", "México", "Pop", true);
        assertThrows(DataIntegrityViolationException.class,
                () -> artistRepository.saveAndFlush(duplicate));

        // Limpiar: eliminar el artista creado
        artistRepository.delete(original);
    }
}
