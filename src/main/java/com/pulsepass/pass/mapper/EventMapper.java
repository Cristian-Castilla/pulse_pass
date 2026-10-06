package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.dto.response.EventResponse;
import com.pulsepass.pass.dto.response.EventSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(
            target = "venueCode",
            source = "venue.code"
    )
    @Mapping(
            target = "venueName",
            source = "venue.name"
    )
    EventResponse toResponse(Event event);

    EventSummaryResponse toSummary(Event event);
}
