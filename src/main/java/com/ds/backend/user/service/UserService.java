package com.ds.backend.user.service;

import com.ds.backend.common.exception.BusinessException;
import com.ds.backend.audit.service.AuditService;
import com.ds.backend.user.dto.UserDtos.*;
import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public Page<UserResponse> list(Boolean active, Role role, String search, Pageable pageable) {
        Specification<User> spec = Specification.unrestricted();
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("department")), like)
            ));
        }
        return userRepository.findAll(spec, pageable).map(UserResponse::from);
    }

    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse create(Long actorUserId, UserCreateRequest request) {
        validatePassword(request.password());
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email already exists");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setRole(request.role() == null ? Role.OPERATOR : request.role());
        user.setDepartment(request.department());
        User saved = userRepository.save(user);
        auditService.record(actorUserId, "CREATE_USER", "user", saved.getId().toString());
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(Long actorUserId, Long id, UserUpdateRequest request) {
        User user = find(id);
        if (request.name() != null) user.setName(request.name());
        if (request.password() != null && !request.password().isBlank()) {
            validatePassword(request.password());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.department() != null) user.setDepartment(request.department());
        user.setUpdatedAt(OffsetDateTime.now());
        auditService.record(actorUserId, "UPDATE_USER", "user", user.getId().toString());
        return UserResponse.from(user);
    }

    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    @Transactional
    public UserResponse changeRole(Long actorUserId, Long id, RoleUpdateRequest request) {
        if (request.role() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        User user = find(id);
        user.setRole(request.role());
        user.setUpdatedAt(OffsetDateTime.now());
        auditService.record(actorUserId, "UPDATE_USER", "user", user.getId().toString());
        return UserResponse.from(user);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
