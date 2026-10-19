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
import com.pulsepass.pass.service.AuthorizationService;
import com.pulsepass.pass.service.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserMapper mapper;
    private final AuthorizationService authorizationService;

    public UserServiceImpl(UserRepository userRepository, UserMapper mapper,
                           UserProfileRepository userProfileRepository,
                           AuthorizationService authorizationService){
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.userProfileRepository = userProfileRepository;
        this.authorizationService = authorizationService;
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
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Email doesn't exist: " + email));

        // PRD §11 USR-02: "Propio usuario, o ADMIN con propósito autorizado".
        // La comparación es por ID persistente (PRD §11.1), no por string de email.
        authorizationService.requireOwnerOrPrivileged(user.getId());

        return mapper.toResponse(user);
    }

    public UserResponse findByUsername(String username){
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Username doesn't exist: " + username));

        // PRD §11 USR-03: "Propio usuario, o ADMIN con propósito autorizado"
        authorizationService.requireOwnerOrPrivileged(user.getId());

        return mapper.toResponse(user);
    }
}
