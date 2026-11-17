package com.pulsepass.pass.repository;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserUsername(String username);

    List<UserRole> findByUserEmailIgnoreCase(String email);

    // Scalar queries avoid Hibernate's derived-exists projection bug with association-based composite IDs.
    @Query("SELECT COUNT(r) > 0 FROM UserRole r WHERE r.user.username = :username AND r.role = :role")
    boolean existsByUserUsernameAndRole(@Param("username") String username, @Param("role") Role role);

    @Query("SELECT COUNT(r) > 0 FROM UserRole r WHERE LOWER(r.user.email) = LOWER(:email) AND r.role = :role")
    boolean existsByUserEmailIgnoreCaseAndRole(@Param("email") String email, @Param("role") Role role);
}
