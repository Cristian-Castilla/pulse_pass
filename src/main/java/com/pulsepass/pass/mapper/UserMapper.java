package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.dto.response.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
