package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.dto.response.VenueResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VenueMapper {

    VenueResponse toResponse(Venue venue);
}
