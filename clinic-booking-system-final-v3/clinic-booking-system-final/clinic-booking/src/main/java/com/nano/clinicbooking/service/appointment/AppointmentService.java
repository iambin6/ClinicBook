package com.nano.clinicbooking.service.appointment;

import com.nano.clinicbooking.dto.response.AppointmentDto;
import com.nano.clinicbooking.enums.AppointmentStatus;
import com.nano.clinicbooking.model.Appointment;
import com.nano.clinicbooking.dto.request.BookAppointmentRequest;
import com.nano.clinicbooking.service.email.sent_appointment.AppointmentAnnouncer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService implements IAppointmentService {

    private final AppointmentCreationService creationService;
    private final AppointmentQueryService queryService;
    private final AppointmentStatusService statusService;
    private final AppointmentRescheduleService rescheduleService;
    private final AppointmentOnlineService onlineService;
    private final AppointmentAnnouncer appointmentAnnouncer;//moi them de vao createappointment

    /** API cũ: Tạo cuộc hẹn **/
    @Override
    @Transactional
    public Appointment createAppointment(BookAppointmentRequest request, Long patientId, Long specialtyId, Long doctorId) {
        var created = creationService.createAppointment(request, patientId, doctorId, specialtyId);
        // Sau khi tạo xong, lấy lại DTO để gửi email
        var dto = queryService.getAppointmentById(created.getId());

        // Send only once the booking transaction has committed successfully.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    appointmentAnnouncer.announceAppointment(dto);
                } catch (Exception ex) {
                    log.error("Booking {} saved, but notification failed", dto.getId(), ex);
                }
            }
        });
        return created;
    }

    /** API cũ: Lấy theo ID **/
    @Override
    public AppointmentDto getAppointmentById(Long id) {
        return queryService.getAppointmentById(id);
    }

    /** API cũ: Lấy tất cả **/
    @Override
    public List<AppointmentDto> getAllAppointments() {
        return queryService.getAllAppointments();
    }

    @Override
    public AppointmentDto updateAppointment(Long id, Appointment request) {
        return null;
    }

    @Override
    public void deleteAppointment(Long id) {
        // TODO: implement delete logic
    }

    /** API cũ: Đổi trạng thái (confirm, check-in, complete, cancel, …) **/
    @Override
    @Transactional
    public AppointmentDto changeStatus(Long id, AppointmentStatus newStatus, Long actorUserId) {
        return statusService.changeStatus(id, newStatus, actorUserId);
    }

    /** API cũ: Đổi ca khám **/
    @Override
    @Transactional
    public AppointmentDto rescheduleAppointment(Long appointmentId, Long newShiftId) {
        return rescheduleService.rescheduleAppointment(appointmentId, newShiftId);
    }

    /** Các API cũ truy vấn **/
    @Override
    public List<AppointmentDto> getAppointmentsByStatusAndDate(String status, LocalDate date) {
        return queryService.getAppointmentsByStatusAndDate(status, date);
    }

    @Override
    public List<AppointmentDto> getAppointmentsByStatus(String status) {
        return queryService.getAppointmentsByStatus(status);
    }

    @Override
    public List<AppointmentDto> getAppointmentsByDoctorAndDate(Long doctorId, LocalDate date) {
        return queryService.getAppointmentsByDoctorAndDate(doctorId, date);
    }

    @Override
    public List<AppointmentDto> getAppointmentsByDoctorAndStatus(Long doctorId, AppointmentStatus status) {
        return queryService.getAppointmentsByDoctorAndStatus(doctorId, status);
    }

    @Override
    public List<AppointmentDto> getAppointmentsByShiftAndStatuses(Long shiftId, List<AppointmentStatus> statuses) {
        return queryService.getAppointmentsByShiftAndStatuses(shiftId, statuses);
    }

    @Override
    public List<AppointmentDto> getAppointmentsByPatientAndStatuses(Long patientId, List<AppointmentStatus> statuses) {
        return queryService.getAppointmentsByPatientAndStatuses(patientId, statuses);
    }

    @Override
    public List<AppointmentDto> getAppointmentsByDate(LocalDate date) {
        return queryService.getAppointmentsByDate(date);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentDto getOnlineMeetingInfoForPatient(Long appointmentId, Long patientId) {
        return onlineService.getOnlineMeetingInfoForPatient(appointmentId, patientId);
    }

    @Override
    @Transactional
    public AppointmentDto startOnlineConsultation(Long appointmentId) {
        return onlineService.startOnlineConsultation(appointmentId);
    }



}
