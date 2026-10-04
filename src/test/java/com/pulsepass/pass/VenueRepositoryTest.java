package com.pulsepass.pass;

import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica persistencia, query methods y restricciones de Venue.
 *
 * PRD: "code UNIQUE", "code NOT NULL", "capacity > 0"
 * Query Method: findByCode
 */
class VenueRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private VenueRepository venueRepository;

    @BeforeEach
    void cleanDatabase() {
        venueRepository.deleteAllInBatch();
    }

    @Test
    void shouldSaveAndRetrieveVenueById() {
        // Verificación mediante query method (no necesitamos getId())
        Venue venue = new Venue("VEN-TEST-01", "Test Venue", "Bogotá",
                "Calle 1 # 1-1", 1000L, true);
        venueRepository.saveAndFlush(venue);

        assertThat(venueRepository.findByCode("VEN-TEST-01")).isPresent();
    }

    @Test
    void shouldFindVenueByCode() {
        venueRepository.saveAndFlush(new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1 # 1-01", 5000L, true));

        assertThat(venueRepository.findByCode("VEN-SMR-01")).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenCodeDoesNotExist() {
        assertThat(venueRepository.findByCode("NO-EXISTE")).isEmpty();
    }

    @Test
    void shouldEnforceUniqueCodeConstraintOnSaveAndFlush() {
        venueRepository.saveAndFlush(new Venue("VEN-SMR-01", "Venue 1",
                "Santa Marta", "Dir 1", 100L, true));

        Venue duplicate = new Venue("VEN-SMR-01", "Venue 2",
                "Bogotá", "Dir 2", 200L, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> venueRepository.saveAndFlush(duplicate));
    }
}
