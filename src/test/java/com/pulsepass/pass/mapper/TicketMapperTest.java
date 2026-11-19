package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TicketMapperTest {
    private final TicketMapper mapper = Mappers.getMapper(TicketMapper.class);

    @Test
    void mapsHolderNameWithoutReplacingEmailOrExposingTheFullProfile() {
        User user = new User("andrea", "andrea@example.com", true);
        user.assignProfile(new UserProfile("Andrea", "Ruiz", "private-phone", "private-city",
                LocalDate.of(1998, 4, 12), user));

        var response = mapper.toResponse(ticket(user));

        assertThat(response).hasFieldOrPropertyWithValue("userFullName", "Andrea Ruiz");
        assertThat(response.userEmail()).isEqualTo("andrea@example.com");
        assertThat(response.getClass().getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "ticketCode", "type", "price", "status", "purchaseDate",
                        "userEmail", "eventCode", "eventName", "userFullName");
    }

    @Test
    void usersWithTheSameNameKeepTheirDistinctEmails() {
        User first = new User("andrea", "andrea@example.com", true);
        User second = new User("andrea2", "andrea2@example.com", true);
        for (User user : new User[]{first, second}) {
            user.assignProfile(new UserProfile("Andrea", "Ruiz", null, null, null, user));
            assertThat(mapper.toResponse(ticket(user)))
                    .hasFieldOrPropertyWithValue("userFullName", "Andrea Ruiz")
                    .hasFieldOrPropertyWithValue("userEmail", user.getEmail());
        }
    }

    @Test
    void missingLegacyProfileKeepsEmailAndDoesNotInventAName() {
        var response = mapper.toResponse(ticket(new User("legacy", "legacy@example.com", true)));

        assertThat(response).hasFieldOrPropertyWithValue("userFullName", null);
        assertThat(response.userEmail()).isEqualTo("legacy@example.com");
    }

    private Ticket ticket(User user) {
        Venue venue = new Venue("VEN", "Sede", "Santa Marta", "Calle 1", 100L, true);
        Event event = new Event("EVT", "Festival", "Descripción", EventCategory.MUSIC,
                EventStatus.PUBLISHED, LocalDateTime.of(2098, 12, 12, 20, 0), null, null, venue);
        return new Ticket("TCK", TicketType.VIP, new BigDecimal("250000"), TicketStatus.PAID,
                LocalDateTime.of(2026, 11, 19, 8, 0), user, event);
    }
}
