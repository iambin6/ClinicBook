package com.nano.clinicbooking.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nano.clinicbooking.enums.AppointmentStatus;
import com.nano.clinicbooking.enums.AppointmentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "specialty"})
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reason;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime appointmentTime;

    // ✅ Đặt unique, không nullable để tránh trùng mã
    @Column(name = "appointment_no", unique = true, nullable = false, length = 50)
    private String appointmentNo;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    private AppointmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"appointment", "owner"})
    private List<PatientInformation> patientInfos = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    @JsonIgnoreProperties({"appointments", "doctor"})
    private DoctorShift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    @JsonIgnoreProperties({"password"})
    private User confirmedBy;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"appointment"})
    private List<Prescription> prescriptions = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private ShiftSlot slot;

    private LocalTime actualStartTime;
    private LocalTime actualEndTime;
    private Boolean checkedIn = false;

    @Enumerated(EnumType.STRING)
    private AppointmentType type = AppointmentType.OFFLINE;

    @Column(length = 2000)
    private String meetingUrl; // Link Jitsi hoặc link thanh toán
    private String joinCode;

    // ✅ Sinh mã duy nhất theo prefix (ONL/OFF) và UUID
    public void generateAppointmentNo(AppointmentType type) {
        String prefix = (type == AppointmentType.ONLINE) ? "ONL" : "OFF";
        this.appointmentNo = prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
