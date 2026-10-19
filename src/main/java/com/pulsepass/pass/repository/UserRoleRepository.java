package com.pulsepass.pass.repository;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserUsername(String username);

    List<UserRole> findByUserEmailIgnoreCase(String email);

    boolean existsByUserUsernameAndRole(String username, Role role);

    boolean existsByUserEmailIgnoreCaseAndRole(String email, Role role);
}
