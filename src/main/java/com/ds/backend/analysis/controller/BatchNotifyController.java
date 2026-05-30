package com.ds.backend.analysis.controller;

import com.ds.backend.auth.service.JwtService;
import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchNotifyController {
    private final JwtService jwtService;

    public BatchNotifyController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/notify")
    public ApiResponse<Void> notify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> payload) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || !jwtService.parseServiceToken(authorization.substring("Bearer ".length()))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
        return ApiResponse.ok(null);
    }
}
