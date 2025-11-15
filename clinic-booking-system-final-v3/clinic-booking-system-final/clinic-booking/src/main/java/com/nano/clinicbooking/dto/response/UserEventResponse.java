package com.nano.clinicbooking.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEventResponse {
    private Long id;
    private Long userId;
    private Long eventId;
    private String eventName;
    private String userFullName;
    private String userEmail;
    private String userPhone;
    private String eventDescription;
    private String eventLocation;
    private LocalDateTime eventStart;
    private LocalDateTime eventEnd;
    private LocalDateTime registeredAt;
    private String status;
}
