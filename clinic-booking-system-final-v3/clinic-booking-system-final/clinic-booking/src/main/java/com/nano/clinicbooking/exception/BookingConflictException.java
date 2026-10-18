package com.nano.clinicbooking.exception;

public class BookingConflictException extends IllegalStateException {
    public BookingConflictException(String message) {
        super(message);
    }
}
