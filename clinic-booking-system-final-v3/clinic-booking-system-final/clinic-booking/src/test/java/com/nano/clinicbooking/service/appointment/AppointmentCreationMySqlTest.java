package com.nano.clinicbooking.service.appointment;

import com.nano.clinicbooking.dto.request.BookAppointmentRequest;
import com.nano.clinicbooking.enums.AppointmentType;
import com.nano.clinicbooking.enums.ShiftType;
import com.nano.clinicbooking.enums.SlotStatus;
import com.nano.clinicbooking.exception.BookingConflictException;
import com.nano.clinicbooking.mapper.AppointmentMapper;
import com.nano.clinicbooking.model.*;
import com.nano.clinicbooking.repository.appointment.AppointmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/** Uses only an explicitly supplied, disposable local MySQL database. */
@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:booking-mysql-test.properties",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AppointmentCreationMySqlTest.JpaConfiguration.class)
@Import({AppointmentCreationService.class, AppointmentMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:.*")
class AppointmentCreationMySqlTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Appointment.class)
    @EnableJpaRepositories("com.nano.clinicbooking.repository")
    static class JpaConfiguration {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("BOOKING_TEST_JDBC_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:\\d+/clinic_booking_slot_test(?:\\?.*)?")) {
            throw new IllegalStateException("Tests require the disposable local clinic_booking_slot_test database");
        }
        properties.add("spring.datasource.url", () -> url);
    }

    @Autowired private AppointmentCreationService service;
    @Autowired private AppointmentRepository appointments;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entities;
    @MockitoBean private SimpMessagingTemplate messaging;

    @Test
    void sequentialBookingsUseDifferentSlots() {
        Fixture fixture = fixture(2);
        Appointment first = book(fixture);
        Appointment second = book(fixture);
        assertThat(first.getSlot().getId()).isNotEqualTo(second.getSlot().getId());
        assertThat(appointments.countByShiftId(fixture.shiftId())).isEqualTo(2);
    }

    @Test
    void concurrentBookingsUseDifferentSlots() throws Exception {
        Fixture fixture = fixture(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { start.await(); return book(fixture); });
            var second = workers.submit(() -> { start.await(); return book(fixture); });
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).getSlot().getId())
                    .isNotEqualTo(second.get(30, TimeUnit.SECONDS).getSlot().getId());
        }
        assertThat(appointments.countByShiftId(fixture.shiftId())).isEqualTo(2);
    }

    @Test
    void concurrentBookingsForTheLastSlotProduceOneBookingAndOneConflict() throws Exception {
        Fixture fixture = fixture(1);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { start.await(); return attemptBooking(fixture); });
            var second = workers.submit(() -> { start.await(); return attemptBooking(fixture); });
            start.countDown();
            List<Object> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Appointment.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(BookingConflictException.class::isInstance)).hasSize(1);
        }
        assertThat(appointments.countByShiftId(fixture.shiftId())).isEqualTo(1);
    }

    @Test
    void selectedProfilesDoNotImplicitlyAddTheAccountOwnerAsAnotherPatient() {
        Fixture fixture = fixture(1);
        Appointment appointment = book(fixture, 10);
        assertThat(appointment.getPatientInfos()).hasSize(10);
        assertThat(appointments.countByShiftId(fixture.shiftId())).isEqualTo(1);
    }

    @Test
    void anAvailableStatusDoesNotAllowReusingAnOccupiedSlot() {
        Fixture fixture = fixture(2);
        Appointment first = book(fixture);
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                entities.find(ShiftSlot.class, first.getSlot().getId()).setStatus(SlotStatus.AVAILABLE));
        Appointment second = book(fixture);
        assertThat(second.getSlot().getId()).isNotEqualTo(first.getSlot().getId());
    }

    @Test
    void fullShiftReturnsAConflictWithoutInsertingAnotherAppointment() {
        Fixture fixture = fixture(1);
        book(fixture);
        assertThatThrownBy(() -> book(fixture))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khung giờ");
        assertThat(appointments.countByShiftId(fixture.shiftId())).isEqualTo(1);
    }

    @Test
    void aCancelledBookingCanReleaseItsSlotForReuse() {
        Fixture fixture = fixture(1);
        Appointment first = book(fixture);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Appointment saved = entities.find(Appointment.class, first.getId());
            saved.setStatus(com.nano.clinicbooking.enums.AppointmentStatus.CANCELLED);
            saved.getSlot().setStatus(SlotStatus.AVAILABLE);
            saved.setSlot(null);
        });
        Appointment next = book(fixture);
        assertThat(next.getSlot().getId()).isEqualTo(first.getSlot().getId());
    }

    private Appointment book(Fixture fixture) {
        return book(fixture, 1);
    }

    private Object attemptBooking(Fixture fixture) {
        try {
            return book(fixture);
        } catch (BookingConflictException exception) {
            return exception;
        }
    }

    private Appointment book(Fixture fixture, int patientCount) {
        BookAppointmentRequest request = new BookAppointmentRequest();
        Appointment details = new Appointment();
        details.setType(AppointmentType.OFFLINE);
        request.setAppointment(details);
        request.setShiftId(fixture.shiftId());
        request.setPatients(IntStream.range(0, patientCount).mapToObj(index -> {
            PatientInformation patient = new PatientInformation();
            patient.setFullName("Test patient " + index);
            return patient;
        }).toList());
        return service.createAppointment(request, fixture.patientId(), fixture.doctorId(), fixture.specialtyId());
    }

    private Fixture fixture(int slotCount) {
        return new TransactionTemplate(transactions).execute(status -> {
            Specialty specialty = new Specialty();
            specialty.setName("Slot test " + UUID.randomUUID());
            entities.persist(specialty);
            User patient = new User();
            fillUser(patient, "Patient");
            entities.persist(patient);
            Doctor doctor = new Doctor();
            fillUser(doctor, "Doctor");
            doctor.setSpecialty(specialty);
            entities.persist(doctor);
            DoctorShift shift = new DoctorShift();
            shift.setDoctor(doctor);
            shift.setDate(LocalDate.now().plusDays(10));
            shift.setShift(ShiftType.MORNING);
            shift.setStartTime(LocalTime.of(8, 0));
            shift.setEndTime(LocalTime.of(12, 0));
            shift.setMaxPatients(10);
            entities.persist(shift);
            IntStream.range(0, slotCount).forEach(index -> {
                ShiftSlot slot = new ShiftSlot();
                slot.setShift(shift);
                slot.setSlotNumber(index + 1);
                slot.setStartTime(LocalTime.of(8 + index, 0));
                slot.setEndTime(LocalTime.of(8 + index, 45));
                entities.persist(slot);
            });
            entities.flush();
            return new Fixture(patient.getId(), doctor.getId(), specialty.getId(), shift.getId());
        });
    }

    private void fillUser(User user, String role) {
        user.setFullName("Booking test " + role);
        user.setEmail(UUID.randomUUID() + "@example.invalid");
        user.setPassword("unused-test-password");
        user.setUserType(role);
    }

    private record Fixture(Long patientId, Long doctorId, Long specialtyId, Long shiftId) {}
}
