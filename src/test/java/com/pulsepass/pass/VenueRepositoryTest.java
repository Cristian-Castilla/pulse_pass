package com.pulsepass.pass;

import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.VenueRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * QT-007 (query method simple) y QT-009 (restricción UNIQUE con saveAndFlush).
 */

class VenueRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private VenueRepository venueRepository;

    @BeforeEach
    void cleanDatabase() {
        venueRepository.deleteAll();
    }

    @Test
    void shouldFindVenueByCode() {
        venueRepository.save(new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1 # 1-01", 5000L, true));

        Optional<Venue> found = venueRepository.findByCode("VEN-SMR-01");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Marina Convention Center");
        assertThat(found.get().getCity()).isEqualTo("Santa Marta");
    }

    @Test
    void shouldReturnEmptyWhenCodeDoesNotExist() {
        Optional<Venue> found = venueRepository.findByCode("NO-EXISTE");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldEnforceUniqueCodeConstraint() {
        venueRepository.save(new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1 # 1-01", 5000L, true));
        venueRepository.flush();

        Venue duplicate = new Venue("VEN-SMR-01", "Otro centro de convenciones",
                "Santa Marta", "Otra dirección", 1000L, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> venueRepository.saveAndFlush(duplicate));
    }
}
