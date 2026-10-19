package com.nano.clinicbooking.service.appointment;

import com.nano.clinicbooking.dto.request.BookAppointmentRequest;
import com.nano.clinicbooking.dto.response.AppointmentDto;
import com.nano.clinicbooking.model.Appointment;
import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.service.email.sent_appointment.AppointmentAnnouncer;
import com.nano.clinicbooking.service.email.sent_appointment.AppointmentEmailContentService;
import com.nano.clinicbooking.service.email.send_register.EmailService;
import com.nano.clinicbooking.repository.appointment.AppointmentRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingEmailTest {
    @AfterEach void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void sendsOnlyAfterCommitAndDoesNotSendOnRollback() {
        var creation = mock(AppointmentCreationService.class);
        var query = mock(AppointmentQueryService.class);
        var announcer = mock(AppointmentAnnouncer.class);
        var service = new AppointmentService(creation, query, null, null, null, announcer);
        var request = new BookAppointmentRequest();
        var appointment = new Appointment();
        appointment.setId(42L);
        var dto = new AppointmentDto();
        dto.setId(42L);
        when(creation.createAppointment(request, 1L, 3L, 2L)).thenReturn(appointment);
        when(query.getAppointmentById(42L)).thenReturn(dto);
        TransactionSynchronizationManager.initSynchronization();
        assertSame(appointment, service.createAppointment(request, 1L, 2L, 3L));
        verifyNoInteractions(announcer);
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        callbacks.forEach(c -> c.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(announcer);
        callbacks.forEach(TransactionSynchronization::afterCommit);
        verify(announcer).announceAppointment(dto);
        doThrow(new RuntimeException("SMTP unavailable")).when(announcer).announceAppointment(dto);
        assertDoesNotThrow(() -> callbacks.forEach(TransactionSynchronization::afterCommit));
    }

    @Test void receiptEscapesPatientContentAndWorksWithoutAIOrDate() {
        var content = new AppointmentEmailContentService();
        var user = new User();
        user.setFullName("<script>alert(1)</script>");
        user.setEmail("patient@example.test");
        var dto = new AppointmentDto();
        dto.setReason("A & B");
        String html = content.buildAppointmentEmailHtml(user, dto, "Đã tiếp nhận");
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("A &amp; B"));
        assertTrue(html.contains("patient@example.test"));
        assertFalse(html.contains("<script>"));
    }

    @Test void sendsReceiptToBookingAccountEvenWhenDateIsMissing() {
        var sender = mock(EmailService.class);
        var repository = mock(AppointmentRepository.class);
        var content = new AppointmentEmailContentService();
        var announcer = new AppointmentAnnouncer(sender, content, repository);
        var patient = new User();
        patient.setEmail("owner@example.test");
        var appointment = new Appointment();
        appointment.setPatient(patient);
        var dto = new AppointmentDto();
        dto.setId(42L);
        when(repository.findById(42L)).thenReturn(Optional.of(appointment));
        announcer.announceAppointment(dto);
        verify(sender).sendToUser(eq(patient), eq("Đã tiếp nhận yêu cầu đặt lịch khám"),
                contains("owner@example.test"), eq(true));
        doThrow(new RuntimeException("SMTP unavailable")).when(sender)
                .sendToUser(any(), anyString(), anyString(), anyBoolean());
        assertDoesNotThrow(() -> announcer.announceAppointment(dto));
    }
}
