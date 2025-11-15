package com.nano.clinicbooking.controller.image;

import com.nano.clinicbooking.dto.response.ImageResponse;
import com.nano.clinicbooking.model.Image;
import com.nano.clinicbooking.service.image.ImageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    // =========================================================
    //                      DOCTOR IMAGE
    // =========================================================

    @GetMapping("/doctors/{doctorId}")
    public ResponseEntity<ImageResponse> getDoctorImage(@PathVariable Long doctorId) {
        return ResponseEntity.ok(imageService.getDoctorImage(doctorId));
    }

    @PostMapping(value = "/doctors/{doctorId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> uploadDoctorImage(
            @PathVariable Long doctorId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(imageService.saveDoctorImage(doctorId, file));
    }

    @PutMapping(value = "/doctors/{doctorId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> updateDoctorImage(
            @PathVariable Long doctorId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(imageService.updateDoctorImage(doctorId, file));
    }

    @DeleteMapping("/doctors/{doctorId}")
    public ResponseEntity<Void> deleteDoctorImage(@PathVariable Long doctorId) {
        imageService.deleteDoctorImage(doctorId);
        return ResponseEntity.noContent().build();
    }


    // =========================================================
    //                   SPECIALTY IMAGE
    // =========================================================

    @GetMapping("/specialties/{specialtyId}")
    public ResponseEntity<ImageResponse> getSpecialtyImage(@PathVariable Long specialtyId) {
        return ResponseEntity.ok(imageService.getSpecialtyImage(specialtyId));
    }

    @PostMapping(value = "/specialties/{specialtyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> uploadSpecialtyImage(
            @PathVariable Long specialtyId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(imageService.saveSpecialtyImage(specialtyId, file));
    }

    @PutMapping(value = "/specialties/{specialtyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> updateSpecialtyImage(
            @PathVariable Long specialtyId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(imageService.updateSpecialtyImage(specialtyId, file));
    }

    @DeleteMapping("/specialties/{specialtyId}")
    public ResponseEntity<Void> deleteSpecialtyImage(@PathVariable Long specialtyId) {
        imageService.deleteSpecialtyImage(specialtyId);
        return ResponseEntity.noContent().build();
    }
}
