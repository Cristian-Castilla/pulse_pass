package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.dto.response.ArtistResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ArtistMapper {

    ArtistResponse toResponse(Artist artist);
}
