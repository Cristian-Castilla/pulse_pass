package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.dto.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "firstName", source = "profile.firstName")
    @Mapping(target = "lastName", source = "profile.lastName")
    @Mapping(target = "birthDate", source = "profile.birthDate")
    UserResponse toResponse(User user);
}
