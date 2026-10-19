package com.pulsepass.pass.repository;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventCode(String eventCode);

    boolean existsByEventCode(String eventCode);

    /**
     * Busca un evento por código solo si está publicado.
     *
     * PRD de Seguridad §11 EVT-02: "Público solo si publicado; para otros estados STAFF/ADMIN,
     * evitando revelar borradores". La decisión D3 del plan fue agregar un método propio en lugar de
     * extender el existente, para no mover el contrato de los 20 endpoints heredados.
     */
    Optional<Event> findByEventCodeAndStatus(String eventCode, EventStatus status);

    List<Event> findByStatusOrderByEventDateAsc(EventStatus status);

    List<Event> findByVenueCode(String venueCode);

    @Query("SELECT DISTINCT e FROM Event e JOIN e.artists a WHERE a.stageName = :stageName")
    List<Event> findByArtistStageName(@Param("stageName") String stageName);

    /**
     * Eventos de un artista filtrados por estado.
     *
     * PRD de Seguridad §11 EVT-06: "Público únicamente para eventos publicados; otros estados
     * requieren STAFF/ADMIN". El filtrado va en la base, no en memoria.
     */
    @Query("SELECT DISTINCT e FROM Event e JOIN e.artists a "
            + "WHERE a.stageName = :stageName AND e.status = :status")
    List<Event> findByArtistStageNameAndStatus(@Param("stageName") String stageName,
                                               @Param("status") EventStatus status);

    @Query("SELECT DISTINCT e FROM Event e JOIN e.artists a WHERE e.venue.city = :city AND a.stageName = :stageName")
    List<Event> findByCityAndArtist(@Param("city") String city, @Param("stageName") String stageName);

    @Query("""
            SELECT DISTINCT e FROM Event e
            JOIN e.artists a
            WHERE e.status = 'PUBLISHED'
              AND e.eventDate > :date
              AND e.venue.city = :city
              AND LOWER(a.stageName) LIKE LOWER(CONCAT('%', :text, '%'))
            ORDER BY e.eventDate ASC
            """)
    List<Event> findRecommendedEvents(@Param("date") LocalDateTime date,
                                      @Param("city") String city,
                                      @Param("text") String text);
}
