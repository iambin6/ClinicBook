package com.nano.clinicbooking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "feedbacks")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User user;

    @Column(nullable = false)
    private Integer rating; // 1 to 5

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment;

    @Column(length = 100)
    private String category; // e.g. "Dịch vụ chung", "Thái độ nhân viên", "Cơ sở vật chất", "Bác sĩ tận tâm"

    @Column(nullable = false)
    @Builder.Default
    private Boolean isAnonymous = false;

    @Column(length = 50)
    @Builder.Default
    private String status = "APPROVED"; // "APPROVED", "PENDING", "HIDDEN"

    @Column(columnDefinition = "TEXT")
    private String adminResponse;

    private LocalDateTime adminRespondedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
