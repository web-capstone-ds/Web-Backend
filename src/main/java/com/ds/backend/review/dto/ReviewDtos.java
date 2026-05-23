package com.ds.backend.review.dto;

import com.ds.backend.review.entity.ReportComment;
import com.ds.backend.review.entity.ReportReview;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}
    public record ReviewRequest(String reviewerRole, String reviewerName, OffsetDateTime reviewedAt) {}
    public record CommentRequest(String author, String content) {}
    public record ReviewResponse(UUID id, String reviewerRole, String reviewerName, OffsetDateTime reviewedAt) {
        public static ReviewResponse from(ReportReview review) {
            return new ReviewResponse(review.getReviewId(), review.getReviewerRole(), review.getReviewerName(), review.getReviewedAt());
        }
    }
    public record CommentResponse(UUID id, String author, String content, OffsetDateTime createdAt) {
        public static CommentResponse from(ReportComment comment) {
            return new CommentResponse(comment.getCommentId(), comment.getAuthor(), comment.getContent(), comment.getCreatedAt());
        }
    }
}
