package com.pulsepass.pass.repository;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventCode(String eventCode);

    List<Event> findByStatusOrderByEventDateAsc(EventStatus status);

    List<Event> findByVenue_Code(String venueCode);

    @Query("SELECT DISTINCT e FROM Event e JOIN e.artists a WHERE a.stageName = :stageName")
    List<Event> findByArtistStageName(@Param("stageName") String stageName);

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
    List<Event> findRecommendedEvents(@Param("date") LocalDate date,
                                      @Param("city") String city,
                                      @Param("text") String text);
}
