package com.ds.backend.review.service;

import com.ds.backend.auth.service.JwtService;
import com.ds.backend.common.exception.BusinessException;
import com.ds.backend.review.dto.ReviewDtos.*;
import com.ds.backend.review.entity.ReportComment;
import com.ds.backend.review.entity.ReportReview;
import com.ds.backend.user.entity.Role;
import org.springframework.http.HttpStatus;
import com.ds.backend.review.repository.ReportCommentRepository;
import com.ds.backend.review.repository.ReportReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {
    private final ReportReviewRepository reviewRepository;
    private final ReportCommentRepository commentRepository;

    public ReviewService(ReportReviewRepository reviewRepository, ReportCommentRepository commentRepository) {
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
    }

    public List<ReviewResponse> reviews(LocalDate date) {
        return reviewRepository.findByReportDate(date).stream().map(ReviewResponse::from).toList();
    }

    @Transactional
    public ReviewResponse createReview(LocalDate date, ReviewRequest request) {
        if (!List.of("assignee", "reviewer").contains(request.reviewerRole())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid reviewer role");
        }
        ReportReview review = new ReportReview();
        review.setReportDate(date);
        review.setReviewerRole(request.reviewerRole());
        review.setReviewerName(request.reviewerName());
        review.setReviewedAt(request.reviewedAt() == null ? OffsetDateTime.now() : request.reviewedAt());
        return ReviewResponse.from(reviewRepository.save(review));
    }

    public List<CommentResponse> comments(LocalDate date) {
        return commentRepository.findByReportDateOrderByCreatedAtDesc(date).stream().map(CommentResponse::from).toList();
    }

    @Transactional
    public CommentResponse createComment(LocalDate date, JwtService.Claims claims, CommentRequest request) {
        ReportComment comment = new ReportComment();
        comment.setReportDate(date);
        comment.setAuthor(claims.email());
        comment.setContent(request.content());
        return CommentResponse.from(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(JwtService.Claims claims, UUID id) {
        ReportComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Comment not found"));
        if (claims.role() != Role.ADMIN && !comment.getAuthor().equals(claims.email())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only the author can delete this comment");
        }
        commentRepository.delete(comment);
    }
}
