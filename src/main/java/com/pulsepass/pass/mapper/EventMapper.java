package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.dto.response.EventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(
            target = "venueCode",
            source = "Venue.code"
    )
    EventResponse toResponse(Event event);
}
