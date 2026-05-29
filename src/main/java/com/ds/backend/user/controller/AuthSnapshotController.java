package com.ds.backend.user.controller;

import com.ds.backend.user.dto.AuthSnapshotResponse;
import com.ds.backend.user.service.AuthSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/snapshot")
public class AuthSnapshotController {

    private final AuthSnapshotService authSnapshotService;

    public AuthSnapshotController(AuthSnapshotService authSnapshotService) {
        this.authSnapshotService = authSnapshotService;
    }

    @GetMapping
    public AuthSnapshotResponse getSnapshot(@RequestParam(name = "since", defaultValue = "0") Long since) {
        return authSnapshotService.getSnapshot(since);
    }
}
