package com.pulsepass.pass.mapper;

import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.service.AuthorizationService;
import com.pulsepass.pass.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserMapperTest {
    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void mapsOnlyRequiredProfileFieldsAlongsideAccountIdentity() {
        User user = new User("andrea", "andrea@example.com", true);
        user.assignProfile(new UserProfile("Andrea", "Ruiz", "private-phone", "private-city",
                LocalDate.of(1998, 4, 12), user));

        var response = mapper.toResponse(user);

        assertThat(response).hasFieldOrPropertyWithValue("firstName", "Andrea")
                .hasFieldOrPropertyWithValue("lastName", "Ruiz")
                .hasFieldOrPropertyWithValue("birthDate", LocalDate.of(1998, 4, 12));
        assertThat(response.email()).isEqualTo("andrea@example.com");
        assertThat(response.getClass().getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "username", "email", "active", "firstName", "lastName", "birthDate");
    }

    @Test
    void missingLegacyProfileDoesNotInventPersonalData() {
        var response = mapper.toResponse(new User("legacy", "legacy@example.com", true));

        assertThat(response).hasFieldOrPropertyWithValue("firstName", null)
                .hasFieldOrPropertyWithValue("lastName", null)
                .hasFieldOrPropertyWithValue("birthDate", null);
    }

    @Test
    void registrationReturnsTheProfileCreatedInTheSameTransaction() {
        UserRepository users = mock(UserRepository.class);
        UserProfileRepository profiles = mock(UserProfileRepository.class);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new UserServiceImpl(users, mapper, profiles, mock(AuthorizationService.class));
        var request = new RegisterUserRequest("andrea", "andrea@example.com", "Andrea", "Ruiz",
                null, null, LocalDate.of(1998, 4, 12), UUID.randomUUID().toString());

        var response = service.register(request);

        assertThat(response).hasFieldOrPropertyWithValue("firstName", "Andrea")
                .hasFieldOrPropertyWithValue("lastName", "Ruiz")
                .hasFieldOrPropertyWithValue("birthDate", request.birthDate());
        verify(profiles).save(argThat(profile -> profile.getUser().getProfile() == profile));
    }
}
