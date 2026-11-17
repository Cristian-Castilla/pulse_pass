package com.pulsepass.pass.security;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.*;
import com.pulsepass.pass.repository.*;
import com.pulsepass.pass.service.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Real filter/controller/service/PostgreSQL requests for all 20 legacy routes; no mocked identity. */
@AutoConfigureMockMvc
@Transactional
class AuthorizationMatrixTest extends PostgresContainerSupport {
    enum Actor {
        ANONYMOUS, NONE, USER(Role.USER), STAFF(Role.STAFF), ADMIN(Role.ADMIN),
        USER_STAFF(Role.USER, Role.STAFF), USER_ADMIN(Role.USER, Role.ADMIN),
        STAFF_ADMIN(Role.STAFF, Role.ADMIN), ALL(Role.USER, Role.STAFF, Role.ADMIN);
        final List<Role> roles;
        Actor(Role... roles) { this.roles = List.of(roles); }
        boolean has(Role role) { return roles.contains(role); }
        boolean operational() { return has(Role.STAFF) || has(Role.ADMIN); }
    }

    enum Route {
        VEN01, VEN02, EVT01, EVT02, EVT03, EVT04, EVT05, EVT06,
        ART01, ART02, ART03, USR01, USR02, USR03, TKT01, TKT02, TKT03, TKT04, TKT05, TKT06;
        boolean owned() { return List.of(USR02, USR03, TKT01, TKT02, TKT03, TKT05).contains(this); }
        int expected(Actor actor, boolean owner) {
            return switch (this) {
                case VEN01, VEN02, EVT02, EVT03, EVT06, ART01, ART02, ART03 -> 200;
                case USR01 -> 201;
                default -> {
                    if (actor == Actor.ANONYMOUS) yield 401;
                    boolean allowed = switch (this) {
                        case EVT01, EVT04, EVT05 -> actor.has(Role.ADMIN);
                        case USR02, USR03, TKT03 -> owner || actor.has(Role.ADMIN);
                        case TKT01 -> owner && actor.has(Role.USER);
                        case TKT02 -> owner || actor.operational();
                        case TKT04, TKT06 -> actor.operational();
                        case TKT05 -> actor.has(Role.ADMIN) || (owner && actor.has(Role.USER));
                        default -> false;
                    };
                    yield allowed ? (this == EVT01 || this == TKT01 ? 201 : 200) : 403;
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired UserProfileRepository profiles;
    @Autowired UserRoleRepository roles;
    @Autowired VenueRepository venues;
    @Autowired ArtistRepository artists;
    @Autowired EventRepository events;
    @Autowired TicketRepository tickets;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate sql;
    private static String passwordHash;
    private Venue venue;
    private Artist artist;
    private Event published;
    private Event draft;

    @BeforeEach
    void fixtures() {
        tickets.deleteAll();
        events.deleteAll();
        artists.deleteAll();
        venues.deleteAll();
        roles.deleteAll();
        profiles.deleteAll();
        credentials.deleteAll();
        users.deleteAll();
        em.flush();
        em.clear();
        if (passwordHash == null) passwordHash = encoder.encode("MatrixOnlyFictitious2026!");
        venue = venues.saveAndFlush(new Venue("MATRIX-VEN", "Matrix venue", "Santa Marta", "Address", 5000L, true));
        artist = artists.saveAndFlush(new Artist("Matrix artist", "Colombia", "Electronic", true));
        published = events.saveAndFlush(event("MATRIX-PUB", EventStatus.PUBLISHED));
        published.addArtist(artist);
        draft = events.saveAndFlush(event("MATRIX-DRAFT", EventStatus.DRAFT));
    }

    private Event event(String code, EventStatus status) {
        return new Event(code, "Matrix event", "Description", EventCategory.MUSIC, status,
                LocalDateTime.now().plusDays(30), 0, null, venue);
    }

    private User user(String name, List<Role> userRoles) {
        User user = users.saveAndFlush(new User(name, name + "@example.com", true));
        profiles.saveAndFlush(new UserProfile("First", "Last", "3001234567", "Santa Marta",
                LocalDate.of(1995, 1, 1), user));
        credentials.saveAndFlush(new UserCredential(user, passwordHash, null));
        for (Role role : userRoles) roles.saveAndFlush(new UserRole(user, role));
        return user;
    }

    private Ticket ticket(String code, TicketStatus status, User owner, Event event) {
        return tickets.saveAndFlush(new Ticket(code, TicketType.GENERAL, new BigDecimal("100000"),
                status, LocalDateTime.now(), owner, event));
    }

    static Stream<Arguments> requests() {
        return Arrays.stream(Route.values()).flatMap(route -> Arrays.stream(Actor.values()).flatMap(actor ->
                (route.owned() && actor != Actor.ANONYMOUS ? Stream.of(true, false) : Stream.of(false))
                        .map(owner -> Arguments.of(route, actor, owner))));
    }

    @ParameterizedTest(name = "{0} {1} owner={2}")
    @MethodSource("requests")
    void realAuthorizationMatrix(Route route, Actor actor, boolean owner) throws Exception {
        User requester = user("requester", actor.roles);
        User target = owner ? requester : user("target", List.of(Role.USER));
        ticket("MATRIX-PAID", TicketStatus.PAID, target, published);
        ticket("MATRIX-USED", TicketStatus.USED, target, published);
        ticket("MATRIX-CANCELLED", TicketStatus.CANCELLED, target, published);
        ticket("MATRIX-RESERVED", TicketStatus.RESERVED, target, published);
        ticket("MATRIX-OTHER-EVENT", TicketStatus.PAID, target, draft);
        String bearer = "Bearer " + jwtService.issueToken(requester.getId()).accessToken();
        MockHttpServletRequestBuilder request = request(route, target);
        if (actor != Actor.ANONYMOUS) request.header("Authorization", bearer);
        List<List<java.util.Map<String, Object>>> before = snapshot();
        int expected = route.expected(actor, owner);
        var result = mvc.perform(request);
        assertThat(result.andReturn().getResponse().getStatus()).as("%s %s owner=%s", route, actor, owner)
                .isEqualTo(expected);
        if (expected >= 400) {
            result.andExpect(jsonPath("$.status").value(expected));
            assertThat(snapshot()).as("Denied requests must not change any business/security row").isEqualTo(before);
            return;
        }
        switch (route) {
            case VEN01 -> result.andExpect(jsonPath("$.code").value("MATRIX-VEN"));
            case VEN02 -> result.andExpect(jsonPath("$[0].code").value("MATRIX-VEN"));
            case ART01, ART02 -> result.andExpect(jsonPath("$.stageName").value("Matrix artist"));
            case ART03 -> result.andExpect(jsonPath("$[0].stageName").value("Matrix artist"));
            case EVT01 -> {
                result.andExpect(jsonPath("$.status").value("DRAFT"));
                assertThat(events.findByEventCode("MATRIX-NEW")).isPresent();
            }
            case EVT02 -> result.andExpect(jsonPath("$.eventCode").value("MATRIX-PUB"));
            case EVT03, EVT06 -> result.andExpect(jsonPath("$[0].eventCode").value("MATRIX-PUB"));
            case EVT04 -> {
                em.flush(); em.clear();
                assertThat(events.findByEventCode("MATRIX-DRAFT").orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
            }
            case EVT05 -> {
                em.flush(); em.clear();
                assertThat(events.findByEventCode("MATRIX-DRAFT").orElseThrow().getArtists())
                        .extracting(Artist::getStageName).containsExactly("Matrix artist");
            }
            case USR01 -> {
                User created = users.findByUsername("newmatrix").orElseThrow();
                assertThat(roles.findByUserUsername("newmatrix")).extracting(UserRole::getRole).containsExactly(Role.USER);
                assertThat(credentials.findById(created.getId())).isPresent();
            }
            case USR02, USR03 -> result.andExpect(jsonPath("$.username").value(target.getUsername()));
            case TKT01 -> {
                em.flush(); em.clear();
                assertThat(tickets.count()).isEqualTo(6);
                Ticket purchased = tickets.findAll().stream().filter(t -> t.getTicketCode().startsWith("TCK-"))
                        .findFirst().orElseThrow();
                assertThat(purchased.getTicketCode()).matches("TCK-[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");
                assertThat(purchased.getUser().getId()).isEqualTo(requester.getId());
                assertThat(purchased.getStatus()).isEqualTo(TicketStatus.PAID);
            }
            case TKT02 -> result.andExpect(jsonPath("$.ticketCode").value("MATRIX-PAID"));
            case TKT03 -> result.andExpect(jsonPath("$.length()").value(5));
            case TKT04 -> result.andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].ticketCode").value("MATRIX-PAID"))
                    .andExpect(jsonPath("$[0].status").value("PAID"));
            case TKT05, TKT06 -> {
                em.flush(); em.clear();
                TicketStatus state = route == Route.TKT05 ? TicketStatus.CANCELLED : TicketStatus.USED;
                assertThat(tickets.findByTicketCode("MATRIX-PAID").orElseThrow().getStatus()).isEqualTo(state);
                if (route == Route.TKT06) {
                    var afterFirstUse = snapshot();
                    mvc.perform(patch("/api/tickets/MATRIX-PAID/use").header("Authorization", bearer))
                            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
                    assertThat(snapshot()).isEqualTo(afterFirstUse);
                }
            }
        }
    }

    private List<List<java.util.Map<String, Object>>> snapshot() {
        em.flush(); em.clear();
        return Stream.of("venues", "artists", "events", "event_artists", "users", "user_profiles",
                        "user_credentials", "user_roles", "tickets")
                .map(table -> sql.queryForList("SELECT * FROM " + table + " ORDER BY 1, 2")).toList();
    }

    private MockHttpServletRequestBuilder request(Route route, User target) {
        return switch (route) {
            case VEN01 -> get("/api/venues/MATRIX-VEN");
            case VEN02 -> get("/api/venues/active");
            case EVT01 -> post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                    {"eventCode":"MATRIX-NEW","name":"New event","description":"Description",
                    "category":"MUSIC","eventDate":"%s","minimumAge":0,"venueCode":"MATRIX-VEN"}
                    """.formatted(LocalDateTime.now().plusDays(60)));
            case EVT02 -> get("/api/events/MATRIX-PUB");
            case EVT03 -> get("/api/events/published");
            case EVT04 -> patch("/api/events/MATRIX-DRAFT/publish");
            case EVT05 -> post("/api/events/MATRIX-DRAFT/artists/{id}", artist.getId());
            case EVT06 -> get("/api/events/by-artist").param("stageName", "Matrix artist");
            case ART01 -> get("/api/artists/{id}", artist.getId());
            case ART02 -> get("/api/artists/by-stage-name").param("stageName", "Matrix artist");
            case ART03 -> get("/api/artists/active");
            case USR01 -> post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
                    {"username":"newmatrix","email":"newmatrix@example.com","firstName":"First",
                    "lastName":"Last","birthDate":"1995-01-01","password":"MatrixOnlyFictitious2026!"}
                    """);
            case USR02 -> get("/api/users/by-email").param("email", target.getEmail());
            case USR03 -> get("/api/users/by-username").param("username", target.getUsername());
            case TKT01 -> post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("""
                    {"userEmail":"%s","eventCode":"MATRIX-PUB","type":"GENERAL"}
                    """.formatted(target.getEmail()));
            case TKT02 -> get("/api/tickets/MATRIX-PAID");
            case TKT03 -> get("/api/tickets/by-user").param("email", target.getEmail());
            case TKT04 -> get("/api/events/MATRIX-PUB/tickets/paid");
            case TKT05 -> patch("/api/tickets/MATRIX-PAID/cancel");
            case TKT06 -> patch("/api/tickets/MATRIX-PAID/use");
        };
    }
}
