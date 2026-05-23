package com.ds.backend.common.exception;

import com.ds.backend.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> business(BusinessException ex) {
        return ResponseEntity.status(ex.status()).body(ApiResponse.fail(Map.of("message", ex.getMessage())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> denied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ApiResponse.fail(Map.of("message", "Forbidden")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(Map.of("message", "Invalid request")));
    }
}
