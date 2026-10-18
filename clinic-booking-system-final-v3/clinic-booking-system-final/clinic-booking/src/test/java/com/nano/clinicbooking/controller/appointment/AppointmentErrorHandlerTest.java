package com.nano.clinicbooking.controller.appointment;

import com.nano.clinicbooking.dto.response.AppointmentDto;
import com.nano.clinicbooking.exception.BookingConflictException;
import com.nano.clinicbooking.model.Appointment;
import com.nano.clinicbooking.service.appointment.IAppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppointmentErrorHandlerTest {
    private IAppointmentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IAppointmentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(service, mock(SimpMessagingTemplate.class)))
                .setControllerAdvice(new AppointmentErrorHandler()).build();
    }

    @Test
    void databaseConflictDoesNotExposeSqlToPatients() throws Exception {
        failWith(new DataIntegrityViolationException("Duplicate entry '1' for key 'appointments.UK8y6yin1cflvk14414e91mdcwm'; insert into appointments"));
        mvc.perform(booking()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Không thể lưu lịch hẹn")))
                .andExpect(content().string(not(containsString("insert into"))))
                .andExpect(content().string(not(containsString("UK8y6yin"))));
    }

    @Test
    void fullShiftReturnsAReadableConflict() throws Exception {
        failWith(new BookingConflictException("Ca khám đã hết khung giờ trống. Vui lòng chọn ca khác."));
        mvc.perform(booking()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("hết khung giờ")));
    }

    @Test
    void concurrentBookingReturnsARetryMessage() throws Exception {
        failWith(new CannotAcquireLockException("database lock diagnostics"));
        mvc.perform(booking()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("đang được cập nhật")))
                .andExpect(content().string(not(containsString("database lock diagnostics"))));
    }

    @Test
    void invalidPatientCountReturnsBadRequest() throws Exception {
        failWith(new IllegalStateException("Số người khám vượt quá giới hạn của ca khám."));
        mvc.perform(booking()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Số người khám")));
    }

    @Test
    void successfulBookingKeepsTheExistingResponseFormat() throws Exception {
        Appointment saved = new Appointment();
        saved.setId(55L);
        AppointmentDto dto = new AppointmentDto();
        dto.setId(55L);
        dto.setAppointmentNo("OFF-TEST");
        when(service.createAppointment(any(), eq(1L), eq(2L), eq(3L))).thenReturn(saved);
        when(service.getAppointmentById(55L)).thenReturn(dto);
        mvc.perform(booking()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentNo").value("OFF-TEST"));
    }

    private void failWith(RuntimeException exception) {
        when(service.createAppointment(any(), eq(1L), eq(2L), eq(3L))).thenThrow(exception);
    }

    private MockHttpServletRequestBuilder booking() {
        return post("/appointments/create?specialtyId=2&doctorId=3")
                .requestAttr("userId", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"shiftId\":4,\"appointment\":{\"type\":\"OFFLINE\"}}");
    }
}
