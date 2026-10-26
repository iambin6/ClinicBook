package com.nano.clinicbooking.service.feedback;

import com.nano.clinicbooking.dto.request.FeedbackRequest;
import com.nano.clinicbooking.dto.response.FeedbackResponse;
import com.nano.clinicbooking.dto.response.FeedbackStatsResponse;
import com.nano.clinicbooking.model.Feedback;
import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.repository.feedback.FeedbackRepository;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    private User patient;

    @BeforeEach
    void setUp() {
        patient = new User();
        patient.setId(10L);
        patient.setFullName("Nguyen Van A");
        patient.setEmail("nguyenvana@gmail.com");
        patient.setUserType("Patient");
    }

    @Test
    void testCreateFeedbackSuccess() {
        FeedbackRequest request = FeedbackRequest.builder()
                .rating(5)
                .title("Dịch vụ tuyệt vời")
                .comment("Bác sĩ rất nhiệt tình và chu đáo")
                .category("Chất lượng khám chữa bệnh")
                .isAnonymous(false)
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> {
            Feedback f = invocation.getArgument(0);
            f.setId(100L);
            f.setCreatedAt(LocalDateTime.now());
            return f;
        });

        FeedbackResponse response = feedbackService.createFeedback(10L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(5, response.getRating());
        assertEquals("Nguyen Van A", response.getPatientName());
        assertTrue(response.getIsOwner());
        verify(feedbackRepository, times(1)).save(any(Feedback.class));
    }

    @Test
    void testCreateFeedbackInvalidRating() {
        FeedbackRequest request = FeedbackRequest.builder()
                .rating(6)
                .comment("Test")
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(patient));

        assertThrows(IllegalArgumentException.class, () -> {
            feedbackService.createFeedback(10L, request);
        });
    }

    @Test
    void testGetPublicFeedbacksAnonymity() {
        Feedback f1 = Feedback.builder()
                .id(1L)
                .user(patient)
                .rating(4)
                .comment("Rất tốt")
                .isAnonymous(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(feedbackRepository.findByStatusOrderByCreatedAtDesc("APPROVED")).thenReturn(List.of(f1));

        // When someone else (user 99L) views it:
        List<FeedbackResponse> responses = feedbackService.getAllPublicFeedbacks(99L);
        assertEquals(1, responses.size());
        assertEquals("Bệnh nhân ẩn danh", responses.get(0).getPatientName());
        assertNull(responses.get(0).getPatientEmail());
        assertFalse(responses.get(0).getIsOwner());

        // When the owner (user 10L) views it:
        List<FeedbackResponse> ownerResponses = feedbackService.getAllPublicFeedbacks(10L);
        assertEquals("Nguyen Van A", ownerResponses.get(0).getPatientName());
        assertTrue(ownerResponses.get(0).getIsOwner());
    }

    @Test
    void testGetStats() {
        when(feedbackRepository.getAverageRating()).thenReturn(4.75);
        when(feedbackRepository.count()).thenReturn(20L);
        when(feedbackRepository.countByRatingGroup()).thenReturn(List.of(
                new Object[]{5, 15L},
                new Object[]{4, 5L}
        ));

        FeedbackStatsResponse stats = feedbackService.getStats();

        assertEquals(4.8, stats.getAverageRating());
        assertEquals(20L, stats.getTotalFeedbacks());
        assertEquals(15L, stats.getStarCounts().get(5));
        assertEquals(5L, stats.getStarCounts().get(4));
        assertEquals(0L, stats.getStarCounts().get(1));
    }

    @Test
    void testDeleteFeedbackForbiddenForOtherPatient() {
        Feedback f = Feedback.builder()
                .id(1L)
                .user(patient)
                .build();

        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(f));

        assertThrows(AccessDeniedException.class, () -> {
            feedbackService.deleteFeedback(1L, 999L, "Patient");
        });
    }

    @Test
    void testDeleteFeedbackAllowedForAdmin() {
        Feedback f = Feedback.builder()
                .id(1L)
                .user(patient)
                .build();

        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(f));

        assertDoesNotThrow(() -> {
            feedbackService.deleteFeedback(1L, 999L, "Admin");
        });

        verify(feedbackRepository, times(1)).delete(f);
    }
}
