package com.ds.backend.user.service;

import com.ds.backend.common.exception.BusinessException;
import com.ds.backend.user.dto.UserCreateRequest;
import com.ds.backend.user.dto.UserDto;
import com.ds.backend.user.dto.UserUpdateRequest;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserDto getMe(String operatorId) {
        return userRepository.findById(operatorId)
                .filter(User::isActive)
                .map(UserDto::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.existsById(request.operatorId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Operator ID already exists");
        }
        User user = new User();
        user.setOperatorId(request.operatorId());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(true);
        
        User saved = userRepository.save(user);
        return UserDto.from(saved);
    }

    @Transactional
    public UserDto updateUser(String id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        
        return UserDto.from(user);
    }

    @Transactional
    public void deactivateUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        user.setActive(false);
    }
}
