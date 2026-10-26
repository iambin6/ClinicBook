package com.nano.clinicbooking.service.feedback;

import com.nano.clinicbooking.dto.request.FeedbackRequest;
import com.nano.clinicbooking.dto.response.FeedbackResponse;
import com.nano.clinicbooking.dto.response.FeedbackStatsResponse;

import java.util.List;

public interface IFeedbackService {

    FeedbackResponse createFeedback(Long userId, FeedbackRequest request);

    List<FeedbackResponse> getMyFeedbacks(Long userId);

    List<FeedbackResponse> getAllPublicFeedbacks(Long currentUserId);

    FeedbackStatsResponse getStats();

    FeedbackResponse updateFeedback(Long id, Long userId, FeedbackRequest request);

    void deleteFeedback(Long id, Long currentUserId, String role);
}
