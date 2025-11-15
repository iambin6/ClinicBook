package com.nano.clinicbooking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transactionNo; // vnp_TxnRef
    private String orderInfo;
    private String amount;
    private String bankCode;
    private String responseCode;
    private String transactionStatus;
    private LocalDateTime payDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    @JsonIgnoreProperties({"doctor", "specialty", "patientInfos"})
    private Appointment appointment;
}
