package com.nano.clinicbooking.repository.image;

import com.nano.clinicbooking.model.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, Long> {
    // Kiểm tra đã có ảnh cho doctor chưa
    boolean existsByDoctor_Id(Long doctorId);
    // Kiểm tra đã có ảnh cho specialty chưa
    boolean existsBySpecialty_Id(Long specialtyId);
    Optional<Image> findByDoctor_Id(Long doctorId);
    Optional<Image> findBySpecialty_Id(Long specialtyId);
    void deleteByDoctor_Id(Long doctorId);
    void deleteBySpecialty_Id(Long specialtyId);
}
