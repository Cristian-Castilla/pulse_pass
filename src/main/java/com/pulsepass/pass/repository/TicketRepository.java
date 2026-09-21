package com.pulsepass.pass.repository;

import com.pulsepass.pass.domain.Ticket;
import com.pulsepass.pass.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketCode(String ticketCode);

    @Query("SELECT t FROM Ticket t JOIN FETCH t.user WHERE t.user.email = :email")
    List<Ticket> findByUserEmail(@Param("email") String email);

    @Query("SELECT t FROM Ticket t JOIN FETCH t.user WHERE t.user.email = :email AND t.status = :status")
    List<Ticket> findByUserEmailAndStatus(@Param("email") String email,
                                         @Param("status") TicketStatus status);

    @Query("SELECT t FROM Ticket t JOIN FETCH t.event WHERE t.event.eventDate > :date ORDER BY t.event.eventDate ASC")
    List<Ticket> findTicketsForFutureEvents(@Param("date") LocalDate date);

    @Query("SELECT t FROM Ticket t JOIN FETCH t.user JOIN FETCH t.event WHERE t.event.eventCode = :eventCode AND t.status = 'PAID'")
    List<Ticket> findPaidTicketsByEventCode(@Param("eventCode") String eventCode);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.event.eventCode = :eventCode AND t.status = 'PAID'")
    Long countPaidTicketsByEventCode(@Param("eventCode") String eventCode);

    boolean existsByTicketCode(String ticketCode);
}
