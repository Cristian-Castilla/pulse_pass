package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.*;
import com.pulsepass.pass.dto.request.CreateEventRequest;
import com.pulsepass.pass.exception.*;
import com.pulsepass.pass.mapper.EventMapper;
import com.pulsepass.pass.repository.*;
import com.pulsepass.pass.service.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceRulesTest {
    @Mock EventRepository events;
    @Mock ArtistRepository artists;
    @Mock VenueRepository venues;
    @Mock EventMapper mapper;
    @Mock AuthorizationService authorization;
    @InjectMocks EventServiceImpl service;

    @Test
    void publishedListingPreservesRepositoryOrderAndMapsEveryRow() {
        Event first = event(EventStatus.PUBLISHED);
        Event second = new Event("SECOND", "Second", "Description", EventCategory.MUSIC, EventStatus.PUBLISHED,
                first.getEventDate().plusDays(1), 0, null, venue(true));
        var a = new com.pulsepass.pass.dto.response.EventSummaryResponse(1L, "RULES-EVT", "Event",
                EventCategory.MUSIC, EventStatus.PUBLISHED, first.getEventDate());
        var b = new com.pulsepass.pass.dto.response.EventSummaryResponse(2L, "SECOND", "Second",
                EventCategory.MUSIC, EventStatus.PUBLISHED, second.getEventDate());
        when(events.findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED)).thenReturn(java.util.List.of(first, second));
        when(mapper.toSummary(first)).thenReturn(a);
        when(mapper.toSummary(second)).thenReturn(b);
        assertThat(service.findPublishedEvents()).containsExactly(a, b);
        verify(events).findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void artistSearchSelectsTheVisibilityQueryBeforeMapping(boolean staff) {
        when(authorization.hasRole(com.pulsepass.pass.domain.Role.STAFF)).thenReturn(staff);
        Event event = event(staff ? EventStatus.DRAFT : EventStatus.PUBLISHED);
        if (staff) when(events.findByArtistStageName("artist")).thenReturn(java.util.List.of(event));
        else when(events.findByArtistStageNameAndStatus("artist", EventStatus.PUBLISHED)).thenReturn(java.util.List.of(event));
        var expected = new com.pulsepass.pass.dto.response.EventSummaryResponse(1L, "RULES-EVT", "Event",
                EventCategory.MUSIC, event.getStatus(), event.getEventDate());
        when(mapper.toSummary(event)).thenReturn(expected);
        assertThat(service.findByArtist("artist")).containsExactly(expected);
        if (staff) verify(events, never()).findByArtistStageNameAndStatus(anyString(), any());
        else verify(events, never()).findByArtistStageName(anyString());
    }

    @Test
    void emptyEventListsDoNotInventResultsAndMissingCodeDoesNotMap() {
        assertThat(service.findPublishedEvents()).isEmpty();
        assertThat(service.findByArtist("missing")).isEmpty();
        assertThrows(ResourceNotFoundException.class, () -> service.findByCode("missing"));
        verifyNoInteractions(mapper);
        verify(events, never()).save(any());
    }

    private Venue venue(boolean active) { return new Venue("RULES-VEN", "Venue", "City", "Address", 100L, active); }
    private Event event(EventStatus status) {
        return new Event("RULES-EVT", "Event", "Description", EventCategory.MUSIC, status,
                LocalDateTime.now().plusDays(30), 0, null, venue(true));
    }
    private Artist artist() { return new Artist("Rules artist", "Colombia", "Pop", true); }
    private void noWriteOrMapping() {
        verify(events, never()).save(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void addArtistPersistsTheActualAssociation() {
        Event event = event(EventStatus.DRAFT);
        Artist artist = artist();
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event));
        when(artists.findById(7L)).thenReturn(Optional.of(artist));
        when(events.save(event)).thenReturn(event);
        service.addArtist("RULES-EVT", 7L);
        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(event);
        assertThat(saved.getValue().getArtists()).containsExactly(artist);
        verify(mapper).toResponse(event);
    }

    @Test
    void addArtistRejectsDuplicateWithoutSaving() {
        Event event = event(EventStatus.DRAFT);
        Artist artist = artist();
        event.addArtist(artist);
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event));
        when(artists.findById(7L)).thenReturn(Optional.of(artist));
        assertThrows(DuplicateResourceException.class, () -> service.addArtist("RULES-EVT", 7L));
        assertThat(event.getArtists()).containsExactly(artist);
        noWriteOrMapping();
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
    void addArtistRejectsFinalStatesWithoutMutating(EventStatus status) {
        Event event = event(status);
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event));
        when(artists.findById(7L)).thenReturn(Optional.of(artist()));
        assertThrows(BusinessRuleException.class, () -> service.addArtist("RULES-EVT", 7L));
        assertThat(event.getArtists()).isEmpty();
        assertThat(event.getStatus()).isEqualTo(status);
        noWriteOrMapping();
    }

    @Test
    void addArtistRejectsMissingEventBeforeArtistLookup() {
        assertThrows(ResourceNotFoundException.class, () -> service.addArtist("missing", 7L));
        verifyNoInteractions(artists);
        noWriteOrMapping();
    }

    @Test
    void addArtistRejectsMissingArtistWithoutSaving() {
        Event event = event(EventStatus.DRAFT);
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event));
        assertThrows(ResourceNotFoundException.class, () -> service.addArtist("RULES-EVT", 7L));
        assertThat(event.getArtists()).isEmpty();
        noWriteOrMapping();
    }

    @Test
    void createSavesActualDraftNotMapperFixture() {
        Venue venue = venue(true);
        when(venues.findByCode("RULES-VEN")).thenReturn(Optional.of(venue));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.create(new CreateEventRequest("RULES-EVT", "Event", "Description", EventCategory.MUSIC,
                LocalDateTime.now().plusDays(30), 0, "RULES-VEN"));
        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(saved.getValue().getVenue()).isSameAs(venue);
        verify(mapper).toResponse(saved.getValue());
    }

    @Test
    void publishMutatesManagedEntityBeforeMapping() {
        Event event = event(EventStatus.DRAFT);
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event));
        doAnswer(invocation -> {
            assertThat(((Event) invocation.getArgument(0)).getStatus()).isEqualTo(EventStatus.PUBLISHED);
            return null;
        }).when(mapper).toResponse(event);
        service.publish("RULES-EVT");
        ArgumentCaptor<Event> mapped = ArgumentCaptor.forClass(Event.class);
        verify(mapper).toResponse(mapped.capture());
        assertThat(mapped.getValue()).isSameAs(event);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        // JPA dirty checking persists this managed entity; publish deliberately does not call save.
    }

    @Test
    void createRejectsDuplicateBeforeVenueLookup() {
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event(EventStatus.DRAFT)));
        assertThrows(DuplicateResourceException.class, () -> service.create(new CreateEventRequest(
                "RULES-EVT", "Event", "Description", EventCategory.MUSIC, LocalDateTime.now().plusDays(30), 0, "RULES-VEN")));
        verifyNoInteractions(venues);
        noWriteOrMapping();
    }

    @Test
    void createRejectsNegativeAgeWithoutSaving() {
        when(venues.findByCode("RULES-VEN")).thenReturn(Optional.of(venue(true)));
        assertThrows(BusinessRuleException.class, () -> service.create(new CreateEventRequest(
                "RULES-EVT", "Event", "Description", EventCategory.MUSIC, LocalDateTime.now().plusDays(30), -1, "RULES-VEN")));
        noWriteOrMapping();
    }

    @Test
    void publishRejectsMissingEvent() {
        assertThrows(ResourceNotFoundException.class, () -> service.publish("missing"));
        noWriteOrMapping();
    }

    @Test
    void publishRejectsPastDateWithoutChangingDraft() {
        Event event = new Event("RULES-EVT", "Event", "Description", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDateTime.now().minusDays(1), 0, null, venue(true));
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event));
        assertThrows(BusinessRuleException.class, () -> service.publish("RULES-EVT"));
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        noWriteOrMapping();
    }

    @Test
    void publishRejectsInactiveVenueWithoutChangingDraft() {
        Event event = new Event("RULES-EVT", "Event", "Description", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDateTime.now().plusDays(30), 0, null, venue(false));
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event));
        assertThrows(BusinessRuleException.class, () -> service.publish("RULES-EVT"));
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        noWriteOrMapping();
    }
}
