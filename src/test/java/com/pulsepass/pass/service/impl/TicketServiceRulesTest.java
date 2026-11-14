package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.*;
import com.pulsepass.pass.dto.request.PurchaseTicketRequest;
import com.pulsepass.pass.exception.*;
import com.pulsepass.pass.mapper.TicketMapper;
import com.pulsepass.pass.repository.*;
import com.pulsepass.pass.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceRulesTest {
    @Mock TicketRepository tickets;
    @Mock UserRepository users;
    @Mock EventRepository events;
    @Mock TicketMapper mapper;
    @Mock AuthorizationService authorization;
    private final PriceCalculator prices = new PriceCalculator();

    private TicketServiceImpl service() { return new TicketServiceImpl(tickets, users, events, mapper, prices, authorization); }
    private User user() { return new User("rulesuser", "rules@example.com", true); }
    private Event event(EventStatus status, LocalDateTime date, int age, long capacity) {
        return new Event("RULES-EVT", "Event", "Description", EventCategory.MUSIC, status, date, age, null,
                new Venue("RULES-VEN", "Venue", "City", "Address", capacity, true));
    }
    private Event futureEvent() { return event(EventStatus.PUBLISHED, LocalDateTime.now().plusDays(30), 0, 100); }
    private Ticket ticket(TicketStatus status, Event event) {
        return new Ticket("RULES-TKT", TicketType.GENERAL, new BigDecimal("100000"), status,
                LocalDateTime.now(), user(), event);
    }
    private PurchaseTicketRequest request() { return new PurchaseTicketRequest("rules@example.com", "RULES-EVT", TicketType.VIP); }
    private void stubPurchase(User user, Event event) {
        when(users.findByEmailIgnoreCase("rules@example.com")).thenReturn(Optional.of(user));
        when(events.findByEventCode("RULES-EVT")).thenReturn(Optional.of(event));
    }
    private void noWrites() {
        verify(tickets, never()).save(any());
        verify(events, never()).save(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void lookupUsesPersistedOwnerAndReturnsMappedTicket() {
        Ticket ticket = ticket(TicketStatus.PAID, futureEvent());
        org.springframework.test.util.ReflectionTestUtils.setField(ticket.getUser(), "id", 7L);
        when(tickets.findByTicketCode("RULES-TKT")).thenReturn(Optional.of(ticket));
        var expected = mock(com.pulsepass.pass.dto.response.TicketResponse.class);
        when(mapper.toResponse(ticket)).thenReturn(expected);
        assertThat(service().findByCode("RULES-TKT")).isSameAs(expected);
        var order = inOrder(authorization, mapper);
        order.verify(authorization).requireOwnerOrPrivileged(7L);
        order.verify(mapper).toResponse(ticket);
    }

    @Test
    void userListingAuthorizesBeforeReadingTicketsAndPreservesOrder() {
        User owner = user();
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "id", 7L);
        when(users.findByEmailIgnoreCase("rules@example.com")).thenReturn(Optional.of(owner));
        Ticket first = ticket(TicketStatus.PAID, futureEvent());
        Ticket second = new Ticket("OLDER", TicketType.GENERAL, BigDecimal.ZERO, TicketStatus.CANCELLED,
                LocalDateTime.now().minusDays(1), owner, futureEvent());
        when(tickets.findByUserEmailIgnoreCaseOrderByPurchaseDateDesc("rules@example.com")).thenReturn(List.of(first, second));
        var a = mock(com.pulsepass.pass.dto.response.TicketResponse.class);
        var b = mock(com.pulsepass.pass.dto.response.TicketResponse.class);
        when(mapper.toResponse(first)).thenReturn(a);
        when(mapper.toResponse(second)).thenReturn(b);
        assertThat(service().findByUserEmail("rules@example.com")).containsExactly(a, b);
        var order = inOrder(authorization, tickets);
        order.verify(authorization).requireOwnerOrAdmin(7L);
        order.verify(tickets).findByUserEmailIgnoreCaseOrderByPurchaseDateDesc("rules@example.com");
    }

    @Test
    void emptyPaidListingDoesNotInventTickets() {
        assertThat(service().findPaidTicketsByEvent("missing")).isEmpty();
        verifyNoInteractions(mapper);
        verify(tickets, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void purchaseCapturesRealPaidTicketAndConditionalSoldOut(long capacity) {
        User user = user();
        Event event = event(EventStatus.PUBLISHED, LocalDateTime.now().plusDays(30), 0, capacity);
        stubPurchase(user, event);
        when(tickets.countByEventEventCodeAndStatus("RULES-EVT", TicketStatus.PAID)).thenReturn(0L, 1L);
        when(tickets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service().purchase(request());
        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getEvent()).isSameAs(event);
        assertThat(saved.getValue().getType()).isEqualTo(TicketType.VIP);
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo("250000");
        assertThat(saved.getValue().getTicketCode()).matches("TCK-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}");
        verify(mapper).toResponse(saved.getValue());
        if (capacity == 1) {
            ArgumentCaptor<Event> soldOut = ArgumentCaptor.forClass(Event.class);
            verify(events).save(soldOut.capture());
            assertThat(soldOut.getValue()).isSameAs(event);
            assertThat(soldOut.getValue().getStatus()).isEqualTo(EventStatus.SOLD_OUT);
        } else {
            verify(events, never()).save(any());
            assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        }
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PUBLISHED")
    void purchaseRejectsEveryNonPublishedStateWithoutSaving(EventStatus status) {
        stubPurchase(user(), event(status, LocalDateTime.now().plusDays(30), 0, 100));
        assertThrows(BusinessRuleException.class, () -> service().purchase(request()));
        noWrites();
    }

    @Test
    void purchaseRejectsMissingEventWithoutSaving() {
        when(users.findByEmailIgnoreCase("rules@example.com")).thenReturn(Optional.of(user()));
        assertThrows(ResourceNotFoundException.class, () -> service().purchase(request()));
        noWrites();
    }

    @Test
    void purchaseRejectsPastEventWithoutSaving() {
        stubPurchase(user(), event(EventStatus.PUBLISHED, LocalDateTime.now().minusDays(1), 0, 100));
        assertThrows(BusinessRuleException.class, () -> service().purchase(request()));
        noWrites();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void purchaseRejectsMissingProfileOrBirthDate(boolean withProfile) {
        User user = user();
        if (withProfile) user.assignProfile(new UserProfile("First", "Last", null, null, null, user));
        stubPurchase(user, event(EventStatus.PUBLISHED, LocalDateTime.now().plusDays(30), 18, 100));
        assertThrows(BusinessRuleException.class, () -> service().purchase(request()));
        noWrites();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void minimumAgeIsMeasuredOnEventDateNotToday(int daysAfterBirthday) {
        LocalDateTime date = LocalDateTime.now().plusDays(30);
        User user = user();
        user.assignProfile(new UserProfile("First", "Last", null, null,
                date.toLocalDate().minusYears(18).plusDays(daysAfterBirthday), user));
        stubPurchase(user, event(EventStatus.PUBLISHED, date, 18, 100));
        if (daysAfterBirthday == 1) {
            assertThrows(BusinessRuleException.class, () -> service().purchase(request()));
            noWrites();
        } else {
            when(tickets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service().purchase(request());
            verify(tickets).save(argThat(ticket -> ticket.getStatus() == TicketStatus.PAID));
        }
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PAID")
    void cancelRejectsAllNonPaidStatesWithoutMutation(TicketStatus status) {
        Ticket ticket = ticket(status, futureEvent());
        when(tickets.findByTicketCode("RULES-TKT")).thenReturn(Optional.of(ticket));
        assertThrows(BusinessRuleException.class, () -> service().cancel("RULES-TKT"));
        assertThat(ticket.getStatus()).isEqualTo(status);
        noWrites();
    }

    @Test
    void cancelRejectsPastEventWithoutMutation() {
        Ticket ticket = ticket(TicketStatus.PAID, event(EventStatus.PUBLISHED, LocalDateTime.now().minusDays(1), 0, 100));
        when(tickets.findByTicketCode("RULES-TKT")).thenReturn(Optional.of(ticket));
        assertThrows(BusinessRuleException.class, () -> service().cancel("RULES-TKT"));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        noWrites();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelAndUseCaptureActualTransitionBeforeSave(boolean use) {
        Ticket ticket = ticket(TicketStatus.PAID, futureEvent());
        when(tickets.findByTicketCode("RULES-TKT")).thenReturn(Optional.of(ticket));
        TicketStatus expected = use ? TicketStatus.USED : TicketStatus.CANCELLED;
        when(tickets.save(any())).thenAnswer(invocation -> {
            assertThat(((Ticket) invocation.getArgument(0)).getStatus()).isEqualTo(expected);
            return invocation.getArgument(0);
        });
        if (use) {
            when(authorization.hasRole(Role.STAFF)).thenReturn(true);
            service().markAsUsed("RULES-TKT");
        } else service().cancel("RULES-TKT");
        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(ticket);
        assertThat(saved.getValue().getStatus()).isEqualTo(expected);
        verify(mapper).toResponse(ticket);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PAID")
    void useRejectsAllNonPaidStatesWithoutMutation(TicketStatus status) {
        Ticket ticket = ticket(status, futureEvent());
        when(tickets.findByTicketCode("RULES-TKT")).thenReturn(Optional.of(ticket));
        when(authorization.hasRole(Role.STAFF)).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> service().markAsUsed("RULES-TKT"));
        assertThat(ticket.getStatus()).isEqualTo(status);
        noWrites();
    }

    @Test
    void userCannotMarkTicketUsedBeforeBusinessValidation() {
        Ticket ticket = ticket(TicketStatus.CANCELLED, futureEvent());
        when(tickets.findByTicketCode("RULES-TKT")).thenReturn(Optional.of(ticket));
        assertThrows(AccessDeniedException.class, () -> service().markAsUsed("RULES-TKT"));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        noWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"lookup", "cancel", "use", "user"})
    void missingResourcesNeverWriteOrMap(String operation) {
        assertThrows(ResourceNotFoundException.class, () -> {
            switch (operation) {
                case "lookup" -> service().findByCode("missing");
                case "cancel" -> service().cancel("missing");
                case "use" -> service().markAsUsed("missing");
                default -> service().findByUserEmail("missing@example.com");
            }
        });
        noWrites();
    }

    @Test
    void pluralPaidTicketsContractMapsEveryRepositoryMatch() {
        Ticket first = ticket(TicketStatus.PAID, futureEvent());
        Ticket second = new Ticket("RULES-TKT-2", TicketType.GENERAL, new BigDecimal("100000"),
                TicketStatus.PAID, LocalDateTime.now(), user(), futureEvent());
        when(tickets.findPaidTicketsByEventCode("RULES-EVT")).thenReturn(List.of(first, second));
        assertThat(service().findPaidTicketsByEvent("RULES-EVT")).hasSize(2);
        verify(mapper).toResponse(first);
        verify(mapper).toResponse(second);
    }
}
