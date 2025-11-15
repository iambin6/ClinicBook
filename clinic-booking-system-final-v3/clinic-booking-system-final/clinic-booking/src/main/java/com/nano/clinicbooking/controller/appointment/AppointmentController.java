package com.nano.clinicbooking.controller.appointment;

import com.nano.clinicbooking.dto.response.AppointmentDto;
import com.nano.clinicbooking.enums.AppointmentStatus;
import com.nano.clinicbooking.model.Appointment;
import com.nano.clinicbooking.dto.request.BookAppointmentRequest;
import com.nano.clinicbooking.dto.response.ApiResponse;
import com.nano.clinicbooking.service.appointment.IAppointmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class  AppointmentController {

    private final IAppointmentService appointmentService;
    private final SimpMessagingTemplate messagingTemplate;



    @PreAuthorize("hasAnyAuthority('Patient','Admin')")
    @PostMapping("/create")
    public ResponseEntity<ApiResponse> create(HttpServletRequest req,
                                              @RequestBody BookAppointmentRequest body,
                                              @RequestParam Long specialtyId,
                                              @RequestParam(required=false) Long doctorId) {
        Long patientId = (Long) req.getAttribute("userId");
        var created = appointmentService.createAppointment(body, patientId, specialtyId, doctorId);
        var dto = appointmentService.getAppointmentById(created.getId());
        return ResponseEntity.ok(new ApiResponse("Appointment created", dto));
    }

    @PreAuthorize("hasAnyAuthority('Admin','Receptionist')")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse> getAll() {
        List<AppointmentDto> list = appointmentService.getAllAppointments();
        return ResponseEntity.ok(new ApiResponse("All appointments", list));
    }

    @PreAuthorize("hasAnyAuthority('Admin','Doctor','Receptionist')")
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse> getByStatusAndDate(
            @PathVariable String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<AppointmentDto> list;

        if (date != null) {
            list = appointmentService.getAppointmentsByStatusAndDate(status, date);
        } else {
            list = appointmentService.getAppointmentsByStatus(status);
        }

        return ResponseEntity.ok(new ApiResponse(
                "Appointments with status " + status + (date != null ? " on " + date : ""),
                list
        ));
    }


    // 👤 Bệnh nhân tham gia cuộc hẹn online
    @PreAuthorize("hasAuthority('Patient')")
    @GetMapping("/join-online/{id}")
    public ResponseEntity<ApiResponse> joinOnlineMeeting(HttpServletRequest req, @PathVariable Long id) {
        Long patientId = (Long) req.getAttribute("userId");
        AppointmentDto dto = appointmentService.getOnlineMeetingInfoForPatient(id, patientId);
        return ResponseEntity.ok(new ApiResponse("Join online meeting", dto));
    }

    // 👩‍⚕️ Bác sĩ bắt đầu buổi khám online
    @PreAuthorize("hasAuthority('Doctor')")
    @GetMapping("/start-online/{id}")
    public ResponseEntity<ApiResponse> startOnlineMeeting(@PathVariable Long id) {
        AppointmentDto dto = appointmentService.startOnlineConsultation(id);
        return ResponseEntity.ok(new ApiResponse("Doctor started online consultation", dto));
    }



    //Luong dat lich
    /** 🟡 RECEPTIONIST/ADMIN — Duyệt lịch */
    @PreAuthorize("hasAnyAuthority('Receptionist','Admin')")
    @PutMapping("/confirm/{id}")
    public ResponseEntity<ApiResponse> confirm(HttpServletRequest req, @PathVariable Long id) {
        Long actorId = (Long) req.getAttribute("userId");
        AppointmentDto dto = appointmentService.changeStatus(id, AppointmentStatus.CONFIRMED, actorId);
        return ResponseEntity.ok(new ApiResponse("Appointment confirmed", dto));
    }

    /** 🟡 RECEPTIONIST — Check-in bệnh nhân */
    @PreAuthorize("hasAnyAuthority('Receptionist','Admin')")
    @PutMapping("/checkin/{id}")
    public ResponseEntity<ApiResponse> checkIn(@PathVariable Long id) {
        AppointmentDto dto = appointmentService.changeStatus(id, AppointmentStatus.CHECKED_IN, null);
        return ResponseEntity.ok(new ApiResponse("Patient checked in successfully", dto));
    }

    /** 🔵 DOCTOR — Bắt đầu khám */
    @PreAuthorize("hasAuthority('Doctor')")
    @PutMapping("/start/{id}")
    public ResponseEntity<ApiResponse> startAppointment(@PathVariable Long id) {
        AppointmentDto dto = appointmentService.changeStatus(id, AppointmentStatus.IN_PROGRESS, null);
        return ResponseEntity.ok(new ApiResponse("Appointment started", dto));
    }

    /** 🔵 DOCTOR — Hoàn tất khám */
    @PreAuthorize("hasAuthority('Doctor')")
    @PutMapping("/complete/{id}")
    public ResponseEntity<ApiResponse> completeAppointment(@PathVariable Long id) {
        AppointmentDto dto = appointmentService.changeStatus(id, AppointmentStatus.COMPLETED, null);
        return ResponseEntity.ok(new ApiResponse("Appointment marked as completed", dto));
    }


    @PreAuthorize("hasAnyAuthority('Receptionist','Patient')")
    @PutMapping("/cancel/{id}")
    public ResponseEntity<ApiResponse> cancelAppointment(@PathVariable Long id) {
        AppointmentDto dto = appointmentService.changeStatus(id, AppointmentStatus.CANCELLED, null);
        return ResponseEntity.ok(new ApiResponse("Appointment canceled", dto));
    }

    @PreAuthorize("hasAnyAuthority('Receptionist','Admin')")
    @PutMapping("/reschedule/{id}")
    public ResponseEntity<ApiResponse> rescheduleAppointment(
            @PathVariable Long id,
            @RequestParam Long newShiftId) {

        AppointmentDto dto = appointmentService.rescheduleAppointment(id, newShiftId);
        return ResponseEntity.ok(new ApiResponse("Appointment rescheduled successfully", dto));
    }






    @PreAuthorize("hasAnyAuthority('Doctor','Receptionist')")
    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse> update(@PathVariable Long id, @RequestBody Appointment body) {
        AppointmentDto updated = appointmentService.updateAppointment(id, body);
        return ResponseEntity.ok(new ApiResponse("Appointment updated", updated));
    }

    @PreAuthorize("hasAuthority('Admin')")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse> delete(@PathVariable Long id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.ok(new ApiResponse("Appointment deleted", null));
    }








    @PreAuthorize("hasAnyAuthority('Admin','Receptionist','Doctor','Patient')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getAppointmentDetail(@PathVariable Long id) {
        var dto = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(new ApiResponse("Appointment details", dto));
    }







    @PreAuthorize("hasAuthority('Doctor')")
    @GetMapping("/by-shift/{shiftId}")
    public ResponseEntity<ApiResponse> getAppointmentsByShift(@PathVariable Long shiftId) {
        List<AppointmentDto> list = appointmentService.getAppointmentsByShiftAndStatuses(
                shiftId,
                List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED)
        );
        return ResponseEntity.ok(new ApiResponse("Appointments for this shift", list));
    }

    //Lễ tân
// 🗓️ Lễ tân xem toàn bộ cuộc hẹn trong ngày
    @PreAuthorize("hasAnyAuthority('Admin','Receptionist')")
    @GetMapping("/by-date")
    public ResponseEntity<ApiResponse> getAppointmentsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        var list = appointmentService.getAppointmentsByDate(date);
        return ResponseEntity.ok(new ApiResponse("Appointments for " + date, list));
    }

    // 👨‍⚕️ Bác sĩ xem lịch hẹn của mình trong ngày
    @PreAuthorize("hasAnyAuthority('Receptionist','Admin')")
    @GetMapping("/by-doctor/{doctorId}")
    public ResponseEntity<ApiResponse> getAppointmentsByDoctorAndDate(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        var list = appointmentService.getAppointmentsByDoctorAndDate(doctorId, date);
        return ResponseEntity.ok(new ApiResponse("Appointments for doctor " + doctorId + " on " + date, list));
    }








//patient

    @PreAuthorize("hasAuthority('Patient')")
    @GetMapping("/my-upcoming")
    public ResponseEntity<ApiResponse> getMyUpcomingAppointments(HttpServletRequest req) {
        Long patientId = (Long) req.getAttribute("userId");
        var list = appointmentService.getAppointmentsByPatientAndStatuses(
                patientId,
                List.of(AppointmentStatus.PENDING_CONFIRMATION, AppointmentStatus.CONFIRMED)
        );
        return ResponseEntity.ok(new ApiResponse("Upcoming appointments", list));
    }

    @PreAuthorize("hasAuthority('Patient')")
    @GetMapping("/my-completed")
    public ResponseEntity<ApiResponse> getMyCompletedAppointments(HttpServletRequest req) {
        Long patientId = (Long) req.getAttribute("userId");
        var list = appointmentService.getAppointmentsByPatientAndStatuses(
                patientId,
                List.of(AppointmentStatus.COMPLETED)
        );
        return ResponseEntity.ok(new ApiResponse("Completed appointments with prescriptions", list));
    }
}

