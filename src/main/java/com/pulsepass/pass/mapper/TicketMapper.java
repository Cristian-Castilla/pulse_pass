package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.Ticket;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.dto.response.TicketResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    @Mapping(target = "userEmail", source = "user.email")
    @Mapping(target = "eventCode", source = "event.eventCode")
    @Mapping(target = "eventName", source = "event.name")
    @Mapping(target = "userFullName", source = "user.profile")
    TicketResponse toResponse(Ticket ticket);

    default String fullName(UserProfile profile) {
        return profile == null ? null : (profile.getFirstName() + " " + profile.getLastName()).trim();
    }
}
