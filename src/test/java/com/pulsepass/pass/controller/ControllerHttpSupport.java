package com.pulsepass.pass.controller;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.UserCredentialRepository;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import com.pulsepass.pass.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Base de los tests de controller de la Fase 3 migrados a HTTP real con Spring Security.
 *
 * <p><b>Por qué existe esta clase.</b> Los cinco {@code @WebMvcTest} de la Fase 3 mockeaban el
 * {@code Service}, así que nunca ejercitaban la cadena de filtros. Cuando la Fase 4 agregó
 * {@code JwtAuthenticationFilter}, esos tests empezaron a fallar: {@code @WebMvcTest} cargaba el
 * filtro pero no su {@code JwtService}, y además todas las rutas quedaban protegidas sin que el
 * test supiera cómo autenticarse. El PRD de Seguridad §17.4 exige que "los tests previos de negocio
 * siguen vigentes y deben adaptarse para configurar identidad en lugar de eliminarse".
 *
 * <p>La adaptación es esta clase: levanta la aplicación completa con PostgreSQL real
 * (Testcontainers) y deja que cada test persista el estado que necesita antes de la petición.
 * Es la opción que el PRD §17.2 prevé: "{@code @WebMvcTest + @Import} de configuración relevante
 * <b>o {@code @SpringBootTest}</b> según el escenario".
 *
 * <p><b>Sin {@code @MockitoBean}.</b> Los servicios son los reales: eso es lo que hace que el test
 * valide la cadena completa (filtro → controlador → servicio → repositorio → PostgreSQL) en vez de
 * solo la forma del contrato HTTP.
 *
 * <p>La clave del {@code @Transactional} de esta clase es que <b>no</b> se revierte el contexto de
 * seguridad: cada petición HTTP se resuelve con el estado persistido en la misma transacción, que
 * es exactamente lo que ocurre en producción con una base real.
 */
@AutoConfigureMockMvc
@Transactional
abstract class ControllerHttpSupport extends PostgresContainerSupport {

    /**
     * Contraseña ficticia compartida por los usuarios de prueba.
     * PRD §17.3: "En tests usar contraseñas y secretos exclusivamente ficticios".
     */
    static final String RAW_PASSWORD = "UnaFraseSeguraLarga2026!";

    @Autowired protected org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected UserRepository userRepository;
    @Autowired protected UserProfileRepository userProfileRepository;
    @Autowired protected UserCredentialRepository userCredentialRepository;
    @Autowired protected UserRoleRepository userRoleRepository;
    @Autowired protected VenueRepository venueRepository;
    @Autowired protected ArtistRepository artistRepository;
    @Autowired protected com.pulsepass.pass.repository.TicketRepository ticketRepository;
    @Autowired protected com.pulsepass.pass.repository.EventRepository eventRepository;

    /**
     * Limpia las tablas que estos tests tocan.
     *
     * <p><b>El orden es obligatorio y no es estético.</b> Las FKs de V1 vinculan
     * {@code events.venue_id → venues.id}, {@code tickets.user_id → users.id} y
     * {@code tickets.event_id → events.id}. Borrar un venue o un usuario que todavía tiene hijos
     * lanza {@code ConstraintViolationException} ("update or delete on table venues violates
     * foreign key constraint fk_event_venue") y revienta el test en el {@code @BeforeEach}, no en
     * la aserción.
     *
     * <p>Se borran primero los hijos y después los padres, en el orden inverso a las FKs:
     * tickets → event_artists → events → artists → venues, y por el lado de usuario
     * user_roles → user_profiles → user_credentials → users.
     *
     * <p>{@code deleteAll()} (no {@code deleteAllInBatch()}) es deliberado: pasa por el ciclo de
     * vida de JPA, carga las asociaciones y respeta {@code orphanRemoval} y las cascadas. Con
     * {@code deleteAllInBatch()} el {@code DELETE} masivo no ve las filas que el contexto de
     * persistencia ya tiene cargadas y el flush posterior reinserta lo que no borró, lo que
     * termina en "duplicate key value violates unique constraint" en el test siguiente.
     *
     * <p>Se ejecuta <b>antes</b> de cada test. No hay limpieza posterior: el {@code @Transactional}
     * revierte la transacción al terminar cada método de test, así que el estado no sobrevive al
     * test. Un {@code @AfterEach} que borre tablas sería contraproducente: corre después del
     * rollback, con la sesión en un estado inconsistente, y el flush posterior reinserta filas que
     * ya se habían revertido (lo que produce "duplicate key value violates unique constraint" en el
     * test siguiente).
     *
     * <p>Esto incluye la semilla V2 (5 artistas): los tests que cuentan artistas crean los suyos y
     * asertan contra lo que crearon, así que no dependen del orden de ejecución.
     */
    @BeforeEach
    void resetState() {
        clearState();
    }

    private void clearState() {
        // Hijos primero (FKs hacia events, users y venues)
        ticketRepository.deleteAll();
        eventRepository.deleteAll();

        // Padres de events y tickets
        artistRepository.deleteAll();
        venueRepository.deleteAll();

        // Lado usuario
        userRoleRepository.deleteAll();
        userProfileRepository.deleteAll();
        userCredentialRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Crea un usuario completo (User + perfil + credencial + rol) y lo devuelve.
     *
     * <p>La credencial es lo que permite autenticarse: sin ella, el filtro rechaza el token porque
     * no encuentra la fila en {@code user_credentials}.
     */
    protected User persistUser(String username, String email, Role role) {
        User user = userRepository.saveAndFlush(new User(username, email, true));
        userProfileRepository.saveAndFlush(new UserProfile(
                "Andrea", "Gomez", "3001234567", "Santa Marta",
                LocalDate.of(1995, 5, 15), user));
        userCredentialRepository.saveAndFlush(new UserCredential(
                user, passwordEncoder.encode(RAW_PASSWORD), null));
        userRoleRepository.saveAndFlush(new UserRole(user, role));
        return user;
    }

    /**
     * Crea el venue de referencia del PRD §16.1 (capacidad 5000, decisión A-03).
     *
     * <p><b>Busca antes de crear.</b> La semilla V2 no inserta venues, pero la prueba manual y los
     * tests previos pueden haber dejado uno con el mismo código: crear otro choca con
     * {@code venues_code_key} ("duplicate key value violates unique constraint"). Si ya existe, se
     * devuelve el existente.
     */
    protected Venue persistReferenceVenue() {
        return venueRepository.findByCode("VEN-SMR-01")
                .orElseGet(() -> venueRepository.saveAndFlush(new Venue(
                        "VEN-SMR-01", "Marina Convention Center", "Santa Marta",
                        "Cra 1 #10-20", 5000L, true)));
    }

    /**
     * Crea un artista persistente, para las rutas de artista y de evento por artista.
     *
     * <p><b>Busca antes de crear.</b> {@code artists.stage_name} es UNIQUE y la semilla V2 ya
     * inserta cinco nombres ("Solar Beat", "Neon Waves", ...), así que reusar uno de ellos choca
     * con {@code artists_stage_name_key}. Este helper se usa con nombres propios de cada test, que
     * no están en la semilla.
     */
    protected Artist persistArtist(String stageName) {
        return artistRepository.findByStageName(stageName)
                .orElseGet(() -> artistRepository.saveAndFlush(
                        new Artist(stageName, "Colombia", "Electronic", true)));
    }
}
