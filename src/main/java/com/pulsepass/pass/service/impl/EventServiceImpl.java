package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.dto.request.CreateEventRequest;
import com.pulsepass.pass.dto.response.EventResponse;
import com.pulsepass.pass.dto.response.EventSummaryResponse;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.DuplicateResourceException;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.EventMapper;
import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.VenueRepository;
import com.pulsepass.pass.service.AuthorizationService;
import com.pulsepass.pass.service.EventService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventMapper mapper;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final ArtistRepository artistRepository;
    private final AuthorizationService authorizationService;

    public EventServiceImpl(EventMapper mapper, EventRepository eventRepository,
                            VenueRepository venueRepository, ArtistRepository artistRepository,
                            AuthorizationService authorizationService){
        this.mapper = mapper;
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.artistRepository = artistRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    @Override
    public EventResponse create(CreateEventRequest request){
        if(eventRepository.findByEventCode(request.eventCode()).isPresent()){
            throw new DuplicateResourceException("Event code already exists: " + request.eventCode());
        }

        Venue venue = venueRepository.findByCode(request.venueCode()).orElseThrow(() -> new ResourceNotFoundException("Venue code doesn't exist: " + request.venueCode()));

        if(venue.getActive() == false){
            throw new BusinessRuleException("Venue must be active at the moment of the creation: " + request.eventCode());
        }

        if(request.eventDate().isBefore(LocalDateTime.now())){
            throw new BusinessRuleException("Event date should be future at the moment of the creation: " + LocalDateTime.now());
        }

        if(request.minimumAge() < 0){
            throw new BusinessRuleException("Minimum age shouldn't be negative.");
        }

        Event event = new Event(
                request.eventCode(),
                request.name(),
                request.description(),
                request.category(),
                EventStatus.DRAFT,
                request.eventDate(),
                request.minimumAge(),
                null,
                venue
        );

        Event saved = eventRepository.save(event);

        return mapper.toResponse(saved);
    }

    @Override
    public EventResponse findByCode(String eventCode){
        // PRD §11 EVT-02: "Público solo si publicado; para otros estados STAFF/ADMIN, evitando
        // revelar borradores".
        //
        // Un borrador NO se expone a un usuario sin rol: se resuelve como inexistente (404 de
        // negocio) y no como 403, para no revelar que existe. STAFF y ADMIN sí lo ven.
        //
        // La regla se evalúa en el servicio y no en la ruta: SecurityConfig no puede expresar
        // "público si el recurso cumple una condición de estado", y PRD §14.3 adv. 1 lo dice
        // explícitamente ("la visibilidad de eventos no publicados deben implementarse en
        // Services/seguridad a nivel de método").
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new ResourceNotFoundException("Event code doesn't exist: " + eventCode));

        if (event.getStatus() != EventStatus.PUBLISHED && !canSeeDrafts()) {
            throw new ResourceNotFoundException("Event code doesn't exist: " + eventCode);
        }

        return mapper.toResponse(event);
    }

    /**
     * Indica si la identidad actual puede ver eventos que no están publicados.
     *
     * PRD §11 EVT-02: "para otros estados STAFF/ADMIN". La decisión de D1 (403 con mensaje genérico)
     * se aplica a recursos ajenos; aquí el borrador se reporta como inexistente, que es la forma de
     * no revelar su existencia sin inventar un 404 falso en la ruta.
     */
    private boolean canSeeDrafts() {
        return authorizationService.hasRole(Role.STAFF) || authorizationService.hasRole(Role.ADMIN);
    }

    @Override
    public List<EventSummaryResponse> findPublishedEvents() {
        return eventRepository.findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED)
                .stream()
                .map(mapper::toSummary)
                .toList();
    }

    @Transactional
    @Override
    public EventResponse publish(String eventCode){

        Event saved = eventRepository.findByEventCode(eventCode).orElseThrow(() -> new ResourceNotFoundException("Event code doesn't exist: " + eventCode));

        if(saved.getStatus() != EventStatus.DRAFT){
            throw new BusinessRuleException("Only DRAFT events can be published, but event " + eventCode + " is " + saved.getStatus());
        }

        if(saved.getEventDate().isBefore(LocalDateTime.now())){
            throw new BusinessRuleException("Cannot publish event " + eventCode + ": event date must be in the future, but was " + saved.getEventDate());
        }

        if (!saved.getVenue().getActive()) {
            throw new BusinessRuleException("Cannot publish event " + eventCode + ": venue " + saved.getVenue().getCode() + " is inactive");
        }

        saved.setStatus(EventStatus.PUBLISHED);

        return mapper.toResponse(saved);
    }

    @Transactional
    @Override
    public EventResponse addArtist(String eventCode, Long artistId){
        Event event = eventRepository.findByEventCode(eventCode).orElseThrow(() -> new ResourceNotFoundException("Cannot add artist: event code " + eventCode + " doesn't exist."));

        Artist artist = artistRepository.findById(artistId).orElseThrow(() -> new ResourceNotFoundException("Artist id doesn't exist: " + artistId));

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.FINISHED){
            throw new BusinessRuleException("Cannot add artists to event " + eventCode + " with status " + event.getStatus());
        }

        if(event.getArtists().contains(artist)){
            throw new DuplicateResourceException("Artist " + artistId + " is already associated to event " + eventCode);
        }

        event.addArtist(artist);

        Event saved = eventRepository.save(event);

        return mapper.toResponse(saved);
    }

    @Override
    public List<EventSummaryResponse> findByArtist(String stageName) {
        // PRD §11 EVT-06: "Público únicamente para eventos publicados; otros estados requieren
        // STAFF/ADMIN". La consulta se filtra por publicación en vez de traer todo y descartar en
        // memoria: el filtrado en la base es lo que evita exponer borradores si el conjunto crece.
        EventStatus status = canSeeDrafts() ? null : EventStatus.PUBLISHED;

        return (status == null
                ? eventRepository.findByArtistStageName(stageName)
                : eventRepository.findByArtistStageNameAndStatus(stageName, status))
                .stream()
                .map(mapper::toSummary)
                .toList();
    }

}
