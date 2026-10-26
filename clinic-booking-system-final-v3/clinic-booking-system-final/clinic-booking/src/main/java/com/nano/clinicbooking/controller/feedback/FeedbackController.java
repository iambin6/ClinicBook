package com.nano.clinicbooking.controller.feedback;

import com.nano.clinicbooking.dto.request.FeedbackRequest;
import com.nano.clinicbooking.dto.response.ApiResponse;
import com.nano.clinicbooking.dto.response.FeedbackResponse;
import com.nano.clinicbooking.dto.response.FeedbackStatsResponse;
import com.nano.clinicbooking.service.feedback.IFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final IFeedbackService feedbackService;

    /**
     * 🟡 Bệnh nhân gửi đánh giá phòng khám
     */
    @PostMapping
    @PreAuthorize("hasAuthority('Patient')")
    public ResponseEntity<ApiResponse> createFeedback(
            @Valid @RequestBody FeedbackRequest request,
            HttpServletRequest req,
            Principal principal) {
        Long userId = getUserId(req, principal);
        FeedbackResponse response = feedbackService.createFeedback(userId, request);
        return ResponseEntity.ok(new ApiResponse("Cảm ơn bạn đã gửi đánh giá cho phòng khám!", response));
    }

    /**
     * 🟡 Bệnh nhân xem các đánh giá của chính mình
     */
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('Patient')")
    public ResponseEntity<ApiResponse> getMyFeedbacks(HttpServletRequest req, Principal principal) {
        Long userId = getUserId(req, principal);
        List<FeedbackResponse> responses = feedbackService.getMyFeedbacks(userId);
        return ResponseEntity.ok(new ApiResponse("Danh sách đánh giá của bạn", responses));
    }

    /**
     * 🟡 Bệnh nhân cập nhật đánh giá của mình
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('Patient')")
    public ResponseEntity<ApiResponse> updateFeedback(
            @PathVariable Long id,
            @RequestBody FeedbackRequest request,
            HttpServletRequest req,
            Principal principal) {
        Long userId = getUserId(req, principal);
        FeedbackResponse response = feedbackService.updateFeedback(id, userId, request);
        return ResponseEntity.ok(new ApiResponse("Cập nhật đánh giá thành công", response));
    }

    /**
     * 🔴 Xóa đánh giá (Bệnh nhân tự xóa hoặc Admin xóa)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Patient', 'Admin')")
    public ResponseEntity<ApiResponse> deleteFeedback(
            @PathVariable Long id,
            HttpServletRequest req,
            Principal principal) {
        Long userId = getUserId(req, principal);
        String role = (String) req.getAttribute("role");
        feedbackService.deleteFeedback(id, userId, role);
        return ResponseEntity.ok(new ApiResponse("Đã xoá đánh giá thành công", null));
    }

    /**
     * 🟢 Xem danh sách đánh giá công khai của phòng khám
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse> getPublicFeedbacks(HttpServletRequest req, Principal principal) {
        Long userId = null;
        try {
            userId = getUserId(req, principal);
        } catch (Exception ignored) {
            // Unauthenticated guest request
        }
        List<FeedbackResponse> responses = feedbackService.getAllPublicFeedbacks(userId);
        return ResponseEntity.ok(new ApiResponse("Danh sách đánh giá phòng khám", responses));
    }

    /**
     * 🟢 Lấy thống kê số sao và đánh giá phòng khám
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getStats() {
        FeedbackStatsResponse stats = feedbackService.getStats();
        return ResponseEntity.ok(new ApiResponse("Thống kê đánh giá", stats));
    }

    private Long getUserId(HttpServletRequest req, Principal principal) {
        Object attr = req.getAttribute("userId");
        if (attr instanceof Long l) {
            return l;
        } else if (attr instanceof Number n) {
            return n.longValue();
        } else if (principal != null && principal.getName() != null) {
            return Long.parseLong(principal.getName());
        }
        throw new IllegalStateException("Không xác định được danh tính người dùng.");
    }
}
