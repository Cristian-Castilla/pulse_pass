package com.pulsepass.pass.service;

import com.pulsepass.pass.dto.request.CreateEventRequest;
import com.pulsepass.pass.dto.response.EventResponse;
import com.pulsepass.pass.dto.response.EventSummaryResponse;

import java.util.List;

public interface EventService {

    EventResponse create(CreateEventRequest request);

    EventResponse findByCode(String eventCode);

    List<EventSummaryResponse> findPublishedEvents();

    EventResponse publish(String eventCode);

    EventResponse addArtist(String eventCode, Long artistId);

    List<EventSummaryResponse> findByArtist(String stageName);
}
