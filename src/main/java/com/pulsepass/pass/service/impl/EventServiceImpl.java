package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventStatus;
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
import com.pulsepass.pass.service.EventService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventMapper mapper;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final ArtistRepository artistRepository;

    public EventServiceImpl(EventMapper mapper, EventRepository eventRepository, VenueRepository venueRepository, ArtistRepository artistRepository){
        this.mapper = mapper;
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.artistRepository = artistRepository;
    }

    @Override
    public EventResponse create(CreateEventRequest request){
        if(eventRepository.findByEventCode(request.eventCode()).isPresent()){
            throw new DuplicateResourceException("Event code already exists: " + request.eventCode());
        }

        Venue venue = venueRepository.findByCode(request.venueCode()).orElseThrow(() -> new ResourceNotFoundException("Venue code doesn't exist: " + request.venueCode()));

        if(venue.getActive() == false){
            throw new BusinessRuleException("Venue must be active at the moment of the creation: " + request.eventCode());
        }

        if(request.eventDate().isBefore(LocalDate.now())){
            throw new BusinessRuleException("Event date should be future at the moment of the creation: " + LocalDate.now());
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
        return eventRepository.findByEventCode(eventCode)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Event code doesn't exist: " + eventCode));
    }

    @Override
    public EventResponse publish(String eventCode){

        Event saved = eventRepository.findByEventCode(eventCode).orElseThrow(() -> new ResourceNotFoundException("Event code doesn't exist: " + eventCode));

        if(saved.getStatus() != EventStatus.DRAFT){
            throw new BusinessRuleException("Only DRAFT events can be published, but event " + eventCode + " is " + saved.getStatus());
        }

        if(saved.getEventDate().isBefore(LocalDate.now())){
            throw new BusinessRuleException("Cannot publish event " + eventCode + ": event date must be in the future, but was " + saved.getEventDate());
        }

        if (!saved.getVenue().getActive()) {
            throw new BusinessRuleException("Cannot publish event " + eventCode + ": venue " + saved.getVenue().getCode() + " is inactive");
        }

        saved.setStatus(EventStatus.PUBLISHED);

        return mapper.toResponse(saved);
    }

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

    public List<EventSummaryResponse> findByArtist(String stageName){

    }

}
