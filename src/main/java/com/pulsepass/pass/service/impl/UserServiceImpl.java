package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.dto.response.UserResponse;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.DuplicateResourceException;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.UserMapper;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.service.UserService;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.attribute.UserPrincipal;
import java.time.LocalDate;

@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserMapper mapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper mapper, UserProfileRepository userProfileRepository){
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterUserRequest request){

        if(userRepository.findByUsername(request.username()).isPresent()){
            throw new DuplicateResourceException("Username " + request.username() + " already exists. Please, choose another username.");
        }

        if(userRepository.findByEmailIgnoreCase(request.email()).isPresent()){
            throw new DuplicateResourceException("Email " + request.email() + " already exists. Please, choose another email.");
        }

        if(request.birthDate().isAfter(LocalDate.now())){
            throw new BusinessRuleException("Birth date: " + request.birthDate() + " must not be future.");
        }

        User user = new User(
                request.username(),
                request.email(),
                true
        );

        UserProfile userProfile = new UserProfile(
                request.firstName(),
                request.lastName(),
                request.phone(),
                request.city(),
                request.birthDate(),
                user
        );

        User saved = userRepository.save(user);

        userProfileRepository.save(userProfile);

        return mapper.toResponse(saved);
    }

    public UserResponse findByEmail(String email){
        return userRepository.findByEmailIgnoreCase(email)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Email doesn't exist: " + email));
    }

    public UserResponse findByUsername(String username){
        return userRepository.findByUsername(username)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Username doesn't exist: " + username));
    }
}
