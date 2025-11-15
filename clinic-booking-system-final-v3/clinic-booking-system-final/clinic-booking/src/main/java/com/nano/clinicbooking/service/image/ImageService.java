package com.nano.clinicbooking.service.image;

import com.nano.clinicbooking.dto.response.ImageResponse;
import com.nano.clinicbooking.model.Doctor;
import com.nano.clinicbooking.model.Image;
import com.nano.clinicbooking.model.Specialty;
import com.nano.clinicbooking.repository.SpecialtyRepository;
import com.nano.clinicbooking.repository.doctor.DoctorRepository;
import com.nano.clinicbooking.repository.image.ImageRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
@Service
@Transactional
public class ImageService {

    private final ImageRepository imageRepository;
    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;

    private final Path rootPath = Paths.get(System.getProperty("user.dir"))
            .getParent()
            .resolve("frontend")
            .resolve("public")
            .resolve("uploads")
            .toAbsolutePath();

    private final String BASE_URL = "http://localhost:8080/uploads/";

    public ImageService(ImageRepository imageRepository,
                        DoctorRepository doctorRepository,
                        SpecialtyRepository specialtyRepository) {
        this.imageRepository = imageRepository;
        this.doctorRepository = doctorRepository;
        this.specialtyRepository = specialtyRepository;
    }

    // ======================================================
    //                    COMMON METHODS
    // ======================================================

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Image cannot be empty");
        }

        if (!file.getContentType().matches("image/(png|jpg|jpeg|webp)")) {
            throw new RuntimeException("Invalid image format");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("Image exceeds 5MB");
        }
    }

    private void deleteOldFile(String url) {
        try {
            if (url == null) return;

            String fileName = url.replace(BASE_URL, "");
            Path oldFile = rootPath.resolve(fileName);

            if (Files.exists(oldFile)) {
                Files.delete(oldFile);
            }

        } catch (Exception ignored) {}
    }

    private String storeFile(MultipartFile file, String folder) throws IOException {
        Path folderPath = rootPath.resolve(folder);
        Files.createDirectories(folderPath);

        String fileName = UUID.randomUUID() + "_" +
                StringUtils.cleanPath(file.getOriginalFilename());

        Path filePath = folderPath.resolve(fileName);
        file.transferTo(filePath.toFile());

        return BASE_URL + folder + "/" + fileName;
    }

    private ImageResponse toDto(Image img) {
        ImageResponse dto = new ImageResponse();
        dto.setId(img.getId());
        dto.setImageUrl(img.getImageUrl());
        return dto;
    }


    // ======================================================
    //                     DOCTOR IMAGE
    // ======================================================

    public ImageResponse getDoctorImage(Long doctorId) {
        Image img = imageRepository.findByDoctor_Id(doctorId)
                .orElseGet(() -> {
                    Image fallback = new Image();
                    fallback.setImageUrl(BASE_URL + "default/default-doctor.jpg");
                    return fallback;
                });
        return toDto(img);
    }

    public ImageResponse saveDoctorImage(Long doctorId, MultipartFile file) {

        validateImage(file);

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (imageRepository.existsByDoctor_Id(doctorId)) {
            throw new RuntimeException("Doctor already has an image");
        }

        String newUrl;
        try {
            newUrl = storeFile(file, "doctors");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image");
        }

        Image img = new Image();
        img.setImageUrl(newUrl);
        img.setDoctor(doctor);

        return toDto(imageRepository.save(img));
    }


    public ImageResponse updateDoctorImage(Long doctorId, MultipartFile file) {

        validateImage(file);

        Image img = imageRepository.findByDoctor_Id(doctorId)
                .orElseGet(() -> {
                    Image newImg = new Image();
                    newImg.setDoctor(doctorRepository.findById(doctorId)
                            .orElseThrow(() -> new RuntimeException("Doctor not found")));
                    return newImg;
                });

        deleteOldFile(img.getImageUrl());

        String newUrl;
        try {
            newUrl = storeFile(file, "doctors");
        } catch (IOException e) {
            throw new RuntimeException("Failed to update image");
        }

        img.setImageUrl(newUrl);
        return toDto(imageRepository.save(img));
    }

    public void deleteDoctorImage(Long doctorId) {
        imageRepository.findByDoctor_Id(doctorId)
                .ifPresent(img -> {
                    deleteOldFile(img.getImageUrl());
                    imageRepository.delete(img);
                });
    }


    // ======================================================
    //                   SPECIALTY IMAGE
    // ======================================================

    public ImageResponse getSpecialtyImage(Long specialtyId) {
        Image img = imageRepository.findBySpecialty_Id(specialtyId)
                .orElseGet(() -> {
                    Image fallback = new Image();
                    fallback.setImageUrl(BASE_URL + "default/default-specialty.jpg");
                    return fallback;
                });
        return toDto(img);
    }

    public ImageResponse saveSpecialtyImage(Long specialtyId, MultipartFile file) {

        validateImage(file);

        Specialty specialty = specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> new RuntimeException("Specialty not found"));

        if (imageRepository.existsBySpecialty_Id(specialtyId)) {
            throw new RuntimeException("Specialty already has an image");
        }

        String newUrl;
        try {
            newUrl = storeFile(file, "specialties");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image");
        }

        Image img = new Image();
        img.setImageUrl(newUrl);
        img.setSpecialty(specialty);

        return toDto(imageRepository.save(img));
    }

    public ImageResponse updateSpecialtyImage(Long specialtyId, MultipartFile file) {

        validateImage(file);

        Image img = imageRepository.findBySpecialty_Id(specialtyId)
                .orElseGet(() -> {
                    Image newImg = new Image();
                    newImg.setSpecialty(specialtyRepository.findById(specialtyId)
                            .orElseThrow(() -> new RuntimeException("Specialty not found")));
                    return newImg;
                });

        deleteOldFile(img.getImageUrl());

        String newUrl;
        try {
            newUrl = storeFile(file, "specialties");
        } catch (IOException e) {
            throw new RuntimeException("Failed to update image");
        }

        img.setImageUrl(newUrl);
        return toDto(imageRepository.save(img));
    }

    public void deleteSpecialtyImage(Long specialtyId) {
        imageRepository.findBySpecialty_Id(specialtyId)
                .ifPresent(img -> {
                    deleteOldFile(img.getImageUrl());
                    imageRepository.delete(img);
                });
    }
}
