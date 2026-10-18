package com.nano.clinicbooking.controller.appointment;

import com.nano.clinicbooking.dto.response.ApiResponse;
import com.nano.clinicbooking.exception.BookingConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AppointmentController.class)
public class AppointmentErrorHandler {
    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ApiResponse> noAvailableSlot(BookingConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse(exception.getMessage(), null));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse> concurrentBooking() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse(
                "Ca khám đang được cập nhật. Vui lòng tải lại danh sách ca khám và thử lại.", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse> invalidBookingData() {
        // Database diagnostics belong in server logs, not patient-facing responses.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse(
                "Không thể lưu lịch hẹn do dữ liệu bị trùng hoặc không hợp lệ. Vui lòng tải lại trang và thử lại.", null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse> invalidBooking(IllegalStateException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse(exception.getMessage(), null));
    }
}
