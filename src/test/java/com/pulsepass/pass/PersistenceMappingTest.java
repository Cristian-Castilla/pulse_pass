package com.pulsepass.pass;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.domain.UserRoleId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceMappingTest {
    @Test
    void rolesUseUserAndRoleAsJpaIdentity() throws Exception {
        IdClass mapping = UserRole.class.getAnnotation(IdClass.class);
        assertThat(mapping).as("roles require a composite identity").isNotNull();
        assertThat(mapping.value().getSimpleName()).isEqualTo("UserRoleId");
        assertThat(Serializable.class).isAssignableFrom(mapping.value());
        assertThat(mapping.value().getDeclaredField("user").getType()).isEqualTo(Long.class);
        assertThat(mapping.value().getDeclaredField("role").getType()).isEqualTo(Role.class);
        assertThat(Arrays.stream(UserRole.class.getDeclaredFields()).filter(f -> f.isAnnotationPresent(Id.class))
                .map(f -> f.getName())).containsExactlyInAnyOrder("user", "role");
    }

    @Test
    void credentialNeverCascadesRemovalToItsUser() throws Exception {
        OneToOne association = UserCredential.class.getDeclaredField("user").getAnnotation(OneToOne.class);
        assertThat(association.cascade()).doesNotContain(CascadeType.ALL, CascadeType.REMOVE);
    }

    @Test
    void roleKeysCompareBothComponents() {
        UserRoleId key = new UserRoleId(10L, Role.USER);
        assertThat(key).isEqualTo(new UserRoleId(10L, Role.USER));
        assertThat(key.hashCode()).isEqualTo(new UserRoleId(10L, Role.USER).hashCode());
        assertThat(key).isNotEqualTo(new UserRoleId(11L, Role.USER))
                .isNotEqualTo(new UserRoleId(10L, Role.STAFF)).isNotEqualTo(null).isNotEqualTo("10:USER");
        assertThat(new UserRoleId()).isEqualTo(new UserRoleId(null, null));
    }
}
