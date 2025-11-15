package com.nano.clinicbooking.controller.recep;

import com.nano.clinicbooking.dto.response.ReceptionEventResponse;
import com.nano.clinicbooking.dto.response.UserEventResponse;
import com.nano.clinicbooking.service.receptionist.ReceptionistEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reception/events")
@RequiredArgsConstructor
public class ReceptionistEventController {

    private final ReceptionistEventService receptionistEventService;

    // ✅ Receptionist xem danh sách sự kiện và người đăng ký
    @PreAuthorize("hasAuthority('Receptionist')")
    @GetMapping
    public ResponseEntity<java.util.List<ReceptionEventResponse>> getEvents() {
        return ResponseEntity.ok(receptionistEventService.getEventsWithRegistrations());
    }


    // ✅ Receptionist xác nhận người dùng đến tham gia event
    @PreAuthorize("hasAuthority('Receptionist')")
    @PostMapping("/{eventId}/confirm/{userId}")
    public ResponseEntity<UserEventResponse> confirmAttendance(
            @PathVariable Long eventId,
            @PathVariable Long userId
    ) {
        UserEventResponse response = receptionistEventService.confirmUserAttendance(eventId, userId);
        return ResponseEntity.ok(response);
    }
}
