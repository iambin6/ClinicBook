package com.nano.clinicbooking.service.appointment;

import com.nano.clinicbooking.dto.response.AppointmentDto;
import com.nano.clinicbooking.enums.AppointmentStatus;
import com.nano.clinicbooking.enums.AppointmentType;
import com.nano.clinicbooking.enums.SlotStatus;
import com.nano.clinicbooking.exception.ResourceNotFoundException;
import com.nano.clinicbooking.mapper.AppointmentMapper;
import com.nano.clinicbooking.model.Appointment;
import com.nano.clinicbooking.model.ShiftSlot;
import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.repository.appointment.AppointmentRepository;
import com.nano.clinicbooking.repository.prescription.PrescriptionRepository;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import com.nano.clinicbooking.repository.shiftSlot.ShiftSlotRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Clean Unit Test for AppointmentStatusService.changeStatus()
 * - Không dùng mock package tự viết
 * - Mock đúng AppointmentMapper
 * - Tránh UnnecessaryStubbing: bỏ stub save, lenient cho toDto
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AppointmentStatusServiceTest {

    @Mock private AppointmentRepository appointmentRepo;
    @Mock private UserRepository userRepo;
    @Mock private ShiftSlotRepository slotRepo;
    @Mock private PrescriptionRepository prescriptionRepo;
    @Mock private AppointmentMapper appointmentMapper;

    @InjectMocks private AppointmentStatusService service;

    private Appointment appointment;
    private ShiftSlot slot;

    @BeforeEach
    void setup() {
        slot = new ShiftSlot();
        slot.setId(100L);
        slot.setStatus(SlotStatus.BOOKED);

        appointment = new Appointment();
        appointment.setId(1L);
        appointment.setStatus(AppointmentStatus.PENDING_CONFIRMATION);
        appointment.setSlot(slot);

        // Mapper -> DTO (lenient do có test ném exception không chạy tới đây)
        lenient().when(appointmentMapper.toDto(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            AppointmentDto dto = new AppointmentDto();
            dto.setId(a.getId());
            dto.setStatus(a.getStatus());
            return dto;
        });

        // KHÔNG stub appointmentRepo.save(...) ở @BeforeEach để tránh unused stub
        // (service không dùng giá trị trả về)
    }

    // ===== CONFIRM =====

    @Test
    void confirm_pending_with_null_actor_ok() {
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        var result = service.changeStatus(1L, AppointmentStatus.CONFIRMED, null);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(userRepo, never()).findById(any());
        verify(appointmentRepo).save(any());
    }

    @Test
    void confirm_pending_with_actor_ok() {
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));

        var result = service.changeStatus(1L, AppointmentStatus.CONFIRMED, 2L);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepo).save(any());
    }

    @Test
    void confirm_online_without_link_autogenerates() {
        appointment.setType(AppointmentType.ONLINE);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        var result = service.changeStatus(1L, AppointmentStatus.CONFIRMED, null);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        Assertions.assertThat(appointment.getMeetingUrl()).isNotBlank();
        Assertions.assertThat(appointment.getJoinCode()).isNotBlank();
    }

    @Test
    void confirm_not_pending_throws() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        Assertions.assertThatThrownBy(() ->
                service.changeStatus(1L, AppointmentStatus.CONFIRMED, null)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirm_actor_not_found_throws() {
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() ->
                service.changeStatus(1L, AppointmentStatus.CONFIRMED, 99L)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== CHECK-IN =====

    @Test
    void checkin_from_confirmed_ok() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        var result = service.changeStatus(1L, AppointmentStatus.CHECKED_IN, null);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
        Assertions.assertThat(appointment.getCheckedIn()).isTrue();
        verify(appointmentRepo).save(any());
    }

    @Test
    void checkin_not_confirmed_throws() {
        appointment.setStatus(AppointmentStatus.PENDING_CONFIRMATION);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        Assertions.assertThatThrownBy(() ->
                service.changeStatus(1L, AppointmentStatus.CHECKED_IN, null)
        ).isInstanceOf(IllegalStateException.class);
    }

    // ===== IN_PROGRESS =====

    @Test
    void start_from_checked_in_sets_start_time() {
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        var result = service.changeStatus(1L, AppointmentStatus.IN_PROGRESS, null);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);
        Assertions.assertThat(appointment.getActualStartTime()).isNotNull();
        verify(appointmentRepo).save(any());
    }

    @Test
    void start_not_checked_in_throws() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        Assertions.assertThatThrownBy(() ->
                service.changeStatus(1L, AppointmentStatus.IN_PROGRESS, null)
        ).isInstanceOf(IllegalStateException.class);
    }

    // ===== COMPLETED =====

    @Test
    void complete_requires_prescription_and_releases_slot() {
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));
        when(prescriptionRepo.existsByAppointmentId(1L)).thenReturn(true);

        var result = service.changeStatus(1L, AppointmentStatus.COMPLETED, null);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        Assertions.assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        verify(slotRepo).save(slot);
        verify(appointmentRepo).save(any());
    }

    @Test
    void complete_without_prescription_throws() {
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));
        when(prescriptionRepo.existsByAppointmentId(1L)).thenReturn(false);

        Assertions.assertThatThrownBy(() ->
                service.changeStatus(1L, AppointmentStatus.COMPLETED, null)
        ).isInstanceOf(IllegalStateException.class);
    }

    // ===== CANCELLED =====

    @Test
    void cancel_releases_slot_if_exists() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        var result = service.changeStatus(1L, AppointmentStatus.CANCELLED, null);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        Assertions.assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        verify(slotRepo).save(slot);
        verify(appointmentRepo).save(any());
    }

    @Test
    void cancel_without_slot_ok_no_save_slot() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setSlot(null);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        var result = service.changeStatus(1L, AppointmentStatus.CANCELLED, null);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(slotRepo, never()).save(any());
        verify(appointmentRepo).save(any());
    }

    @Test
    void cancel_completed_throws() {
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        Assertions.assertThatThrownBy(() ->
                service.changeStatus(1L, AppointmentStatus.CANCELLED, null)
        ).isInstanceOf(IllegalStateException.class);
    }

    // ===== RESCHEDULED + ERROR =====

    @Test
    void rescheduled_sets_status_only() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        var result = service.changeStatus(1L, AppointmentStatus.RESCHEDULED, null);

        Assertions.assertThat(result.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        verify(appointmentRepo).save(any());
    }

    @Test
    void invalid_status_null_throws() {
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appointment));

        Assertions.assertThatThrownBy(() ->
                service.changeStatus(1L, null, null)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appointment_not_found_throws() {
        when(appointmentRepo.findById(404L)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() ->
                service.changeStatus(404L, AppointmentStatus.CONFIRMED, null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }
}
