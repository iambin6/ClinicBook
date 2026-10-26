package com.nano.clinicbooking.dto.response;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackStatsResponse {

    private Double averageRating;
    private Long totalFeedbacks;
    private Map<Integer, Long> starCounts; // key: 1..5, value: count
}
