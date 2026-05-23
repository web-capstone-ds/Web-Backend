package com.ds.backend.review.controller;

import com.ds.backend.auth.service.JwtService;
import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.review.dto.ReviewDtos.*;
import com.ds.backend.review.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/{date}")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
    public ApiResponse<List<ReviewResponse>> reviews(@PathVariable LocalDate date) {
        return ApiResponse.ok(service.reviews(date));
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(@PathVariable LocalDate date, @RequestBody ReviewRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createReview(date, request)));
    }

    @GetMapping("/comments")
    @PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
    public ApiResponse<List<CommentResponse>> comments(@PathVariable LocalDate date) {
        return ApiResponse.ok(service.comments(date));
    }

    @PostMapping("/comments")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(@PathVariable LocalDate date,
                                                                      @org.springframework.security.core.annotation.AuthenticationPrincipal JwtService.Claims claims,
                                                                      @RequestBody CommentRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createComment(date, claims, request)));
    }

    @DeleteMapping("/comments/{id}")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public ApiResponse<String> deleteComment(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtService.Claims claims,
                                             @PathVariable UUID id) {
        service.deleteComment(claims, id);
        return ApiResponse.ok("deleted");
    }
}
