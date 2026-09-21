package com.pulsepass.pass;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.repository.ArtistRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * QT-001/QT-007 (verifica el catálogo sembrado por V2 y query method simple)
 * y QT-009 (restricción UNIQUE en stage_name).
 */

class ArtistRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private ArtistRepository artistRepository;

    private Long createdArtistId;

    @AfterEach
    void cleanUpOwnData() {
        if (createdArtistId != null) {
            artistRepository.deleteById(createdArtistId);
            createdArtistId = null;
        }
    }

    @Test
    void shouldFindArtistFromSeedDataByStageName() {
        Optional<Artist> found = artistRepository.findByStageName("Solar Beat");

        assertThat(found).isPresent();
        assertThat(found.get().getCountry()).isEqualTo("Colombia");
        assertThat(found.get().getGenre()).isEqualTo("Electrónica");
    }

    @Test
    void shouldReturnEmptyWhenStageNameDoesNotExist() {
        Optional<Artist> found = artistRepository.findByStageName("No Existe Como Artista");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldEnforceUniqueStageNameConstraint() {
        Artist original = artistRepository.saveAndFlush(
                new Artist("Unique Stage Name Test", "Colombia", "Rock", true));
        createdArtistId = original.getId();

        Artist duplicate = new Artist("Unique Stage Name Test", "México", "Pop", true);

        assertThrows(DataIntegrityViolationException.class,
                () -> artistRepository.saveAndFlush(duplicate));
    }
}
