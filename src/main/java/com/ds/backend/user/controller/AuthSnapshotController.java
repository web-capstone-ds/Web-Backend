package com.ds.backend.user.controller;

import com.ds.backend.user.dto.AuthSnapshotResponse;
import com.ds.backend.user.service.AuthSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/snapshot")
public class AuthSnapshotController {

    private static final Logger log = LoggerFactory.getLogger(AuthSnapshotController.class);

    private final AuthSnapshotService authSnapshotService;

    public AuthSnapshotController(AuthSnapshotService authSnapshotService) {
        this.authSnapshotService = authSnapshotService;
    }

    @GetMapping
    public AuthSnapshotResponse getSnapshot(@RequestParam(name = "since", defaultValue = "0") Long since,
                                            HttpServletRequest request) {
        AuthSnapshotResponse response = authSnapshotService.getSnapshot(since);
        log.info("auth_snapshot_requested since={} version={} userCount={} remoteAddr={} userAgent={}",
                since,
                response.version(),
                response.users() == null ? 0 : response.users().size(),
                clientIp(request),
                request.getHeader("User-Agent"));
        return response;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
