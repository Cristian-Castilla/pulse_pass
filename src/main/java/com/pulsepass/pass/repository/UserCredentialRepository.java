package com.pulsepass.pass.repository;

import com.pulsepass.pass.domain.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {

    Optional<UserCredential> findByUserUsername(String username);

    Optional<UserCredential> findByUserEmailIgnoreCase(String email);

    boolean existsByUserUsername(String username);

    boolean existsByUserEmailIgnoreCase(String email);
}
