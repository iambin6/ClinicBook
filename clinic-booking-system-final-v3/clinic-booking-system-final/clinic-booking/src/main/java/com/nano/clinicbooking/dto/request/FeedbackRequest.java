package com.nano.clinicbooking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackRequest {

    @NotNull(message = "Số sao đánh giá không được để trống")
    @Min(value = 1, message = "Đánh giá tối thiểu là 1 sao")
    @Max(value = 5, message = "Đánh giá tối đa là 5 sao")
    private Integer rating;

    private String title;

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    private String comment;

    private String category;

    @Builder.Default
    private Boolean isAnonymous = false;
}
