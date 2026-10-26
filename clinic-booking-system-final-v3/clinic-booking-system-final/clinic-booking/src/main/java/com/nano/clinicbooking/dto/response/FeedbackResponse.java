package com.nano.clinicbooking.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponse {

    private Long id;
    private Long userId;
    private String patientName;
    private String patientEmail;
    private Integer rating;
    private String title;
    private String comment;
    private String category;
    private Boolean isAnonymous;
    private String adminResponse;
    private LocalDateTime adminRespondedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isOwner;
}
