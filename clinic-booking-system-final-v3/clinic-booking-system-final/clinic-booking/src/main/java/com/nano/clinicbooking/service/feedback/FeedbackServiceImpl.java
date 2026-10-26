package com.nano.clinicbooking.service.feedback;

import com.nano.clinicbooking.dto.request.FeedbackRequest;
import com.nano.clinicbooking.dto.response.FeedbackResponse;
import com.nano.clinicbooking.dto.response.FeedbackStatsResponse;
import com.nano.clinicbooking.model.Feedback;
import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.repository.feedback.FeedbackRepository;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements IFeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FeedbackResponse createFeedback(Long userId, FeedbackRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + userId));

        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Số sao đánh giá phải từ 1 đến 5");
        }
        if (request.getComment() == null || request.getComment().trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung đánh giá không được để trống");
        }

        Feedback feedback = Feedback.builder()
                .user(user)
                .rating(request.getRating())
                .title(request.getTitle() != null ? request.getTitle().trim() : "")
                .comment(request.getComment().trim())
                .category(request.getCategory() != null && !request.getCategory().isBlank()
                        ? request.getCategory().trim() : "Dịch vụ chung")
                .isAnonymous(Boolean.TRUE.equals(request.getIsAnonymous()))
                .status("APPROVED")
                .build();

        Feedback saved = feedbackRepository.save(feedback);
        return mapToResponse(saved, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackResponse> getMyFeedbacks(Long userId) {
        List<Feedback> list = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return list.stream().map(f -> mapToResponse(f, userId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackResponse> getAllPublicFeedbacks(Long currentUserId) {
        List<Feedback> list = feedbackRepository.findByStatusOrderByCreatedAtDesc("APPROVED");
        return list.stream().map(f -> mapToResponse(f, currentUserId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackStatsResponse getStats() {
        Double avg = feedbackRepository.getAverageRating();
        long total = feedbackRepository.count();

        Map<Integer, Long> starCounts = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            starCounts.put(i, 0L);
        }

        List<Object[]> groups = feedbackRepository.countByRatingGroup();
        if (groups != null) {
            for (Object[] row : groups) {
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    Integer rating = ((Number) row[0]).intValue();
                    Long count = ((Number) row[1]).longValue();
                    starCounts.put(rating, count);
                }
            }
        }

        double roundedAvg = 0.0;
        if (avg != null && !avg.isNaN()) {
            roundedAvg = BigDecimal.valueOf(avg)
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return FeedbackStatsResponse.builder()
                .averageRating(roundedAvg)
                .totalFeedbacks(total)
                .starCounts(starCounts)
                .build();
    }

    @Override
    @Transactional
    public FeedbackResponse updateFeedback(Long id, Long userId, FeedbackRequest request) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá với ID: " + id));

        if (!feedback.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền sửa đánh giá này");
        }

        if (request.getRating() != null) {
            if (request.getRating() < 1 || request.getRating() > 5) {
                throw new IllegalArgumentException("Số sao đánh giá phải từ 1 đến 5");
            }
            feedback.setRating(request.getRating());
        }
        if (request.getTitle() != null) {
            feedback.setTitle(request.getTitle().trim());
        }
        if (request.getComment() != null) {
            if (request.getComment().trim().isEmpty()) {
                throw new IllegalArgumentException("Nội dung đánh giá không được để trống");
            }
            feedback.setComment(request.getComment().trim());
        }
        if (request.getCategory() != null) {
            feedback.setCategory(request.getCategory().trim());
        }
        if (request.getIsAnonymous() != null) {
            feedback.setIsAnonymous(request.getIsAnonymous());
        }

        Feedback updated = feedbackRepository.save(feedback);
        return mapToResponse(updated, userId);
    }

    @Override
    @Transactional
    public void deleteFeedback(Long id, Long currentUserId, String role) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá với ID: " + id));

        boolean isAdmin = "Admin".equalsIgnoreCase(role);
        boolean isOwner = feedback.getUser().getId().equals(currentUserId);

        if (!isAdmin && !isOwner) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xoá đánh giá này");
        }

        feedbackRepository.delete(feedback);
    }

    private FeedbackResponse mapToResponse(Feedback feedback, Long currentUserId) {
        boolean isOwner = currentUserId != null && currentUserId.equals(feedback.getUser().getId());

        String name;
        String email;

        if (Boolean.TRUE.equals(feedback.getIsAnonymous()) && !isOwner) {
            name = "Bệnh nhân ẩn danh";
            email = null;
        } else {
            name = feedback.getUser().getFullName();
            email = maskEmail(feedback.getUser().getEmail());
        }

        return FeedbackResponse.builder()
                .id(feedback.getId())
                .userId(isOwner ? feedback.getUser().getId() : null)
                .patientName(name)
                .patientEmail(email)
                .rating(feedback.getRating())
                .title(feedback.getTitle())
                .comment(feedback.getComment())
                .category(feedback.getCategory())
                .isAnonymous(feedback.getIsAnonymous())
                .adminResponse(feedback.getAdminResponse())
                .adminRespondedAt(feedback.getAdminRespondedAt())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .isOwner(isOwner)
                .build();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return email;
        }
        String first = email.substring(0, 2);
        String domain = email.substring(atIndex);
        return first + "***" + domain;
    }
}
