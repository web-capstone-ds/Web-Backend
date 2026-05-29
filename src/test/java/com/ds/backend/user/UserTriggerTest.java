package com.ds.backend.user;

import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class UserTriggerTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @Transactional
    void versionShouldIncrementOnUpdate() {
        // Given
        User user = new User();
        user.setOperatorId("test-op-1");
        user.setPasswordHash("hash");
        user.setRole(Role.OPERATOR);
        user.setActive(true);
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User savedUser = userRepository.findById("test-op-1").orElseThrow();
        Long initialVersion = savedUser.getVersion();
        assertThat(initialVersion).isNotNull();

        // When
        savedUser.setRole(Role.ENGINEER);
        userRepository.saveAndFlush(savedUser);
        entityManager.clear();

        // Then
        User updatedUser = userRepository.findById("test-op-1").orElseThrow();
        assertThat(updatedUser.getVersion()).isEqualTo(2);
        assertThat(updatedUser.getUpdatedAt()).isNotNull();
    }
}
