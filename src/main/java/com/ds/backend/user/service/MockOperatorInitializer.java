package com.ds.backend.user.service;

import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 모바일 목데이터(MockUsersUtil)와 동일한 5명을 시드한다.
 * 비밀번호는 평문이 아닌 BCrypt 해시로 저장한다.
 * seed.mock-operators=false 로 비활성화할 수 있다(운영 환경 권장).
 */
@Component
@Order(20)
public class MockOperatorInitializer implements ApplicationRunner {

    private record MockUser(String operatorId, String name, String rawPassword,
                            String department, Role role, String phone) {}

    // MockUsersUtil.java 의 SERVER_USERS 와 1:1 대응 (검사원 → INSPECTOR)
    private static final List<MockUser> MOCK_USERS = List.of(
            new MockUser("EMP001", "유민호", "1234", "A동",        Role.ADMIN,     "010-1789-6815"),
            new MockUser("EMP002", "최민수", "5678", "B동",        Role.OPERATOR,  "010-6582-7892"),
            new MockUser("EMP003", "송기환", "9012", "C동",        Role.OPERATOR,  "010-1567-4568"),
            new MockUser("EMP004", "이재혁", "3456", "품질관리팀", Role.INSPECTOR, "010-3113-6985"),
            new MockUser("EMP005", "정연우", "3456", "품질관리팀", Role.INSPECTOR, "010-3113-6323")
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public MockOperatorInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                   @Value("${seed.mock-operators:true}") boolean enabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        for (MockUser m : MOCK_USERS) {
            if (userRepository.existsById(m.operatorId())) {
                continue;
            }
            User user = new User();
            user.setOperatorId(m.operatorId());
            user.setPasswordHash(passwordEncoder.encode(m.rawPassword()));
            user.setName(m.name());
            user.setDepartment(m.department());
            user.setPhone(m.phone());
            user.setRole(m.role());
            user.setActive(true);
            userRepository.save(user);
        }
    }
}
