package com.nano.clinicbooking.service.grouphealth;

import com.nano.clinicbooking.dto.response.groupheath.DoctorGroupAssignmentView;
import com.nano.clinicbooking.enums.ResultStatus;
import com.nano.clinicbooking.model.groupheath.GroupHealthAssignment;
import com.nano.clinicbooking.model.groupheath.GroupHealthRequest;
import com.nano.clinicbooking.repository.grouphealth.GroupHealthRequestRepository;
import com.nano.clinicbooking.repository.grouphealth.GroupHealthAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorGroupHealthServiceImpl implements DoctorGroupHealthService {

    private final GroupHealthAssignmentRepository assignmentRepository;
    private final GroupHealthRequestRepository groupHealthRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DoctorGroupAssignmentView> getAssignmentsForDoctor(Long doctorId, LocalDate date) {

        List<GroupHealthAssignment> list =
                assignmentRepository.findByDoctorIdAndShiftDate(doctorId, date);

        log.info("Found {} assignments for doctor {} on date {}", list.size(), doctorId, date);

        return list.stream()
                .map(a -> DoctorGroupAssignmentView.builder()
                        .assignmentId(a.getId())
                        .requestId(a.getRequest().getId())
                        .groupName(a.getRequest().getGroupName())
                        .specialtyId(a.getSpecialty().getId())
                        .specialtyName(a.getSpecialty().getName())
                        .shiftId(a.getShift().getId())
                        .shiftDate(a.getShift().getDate())
                        .shiftType(a.getShift().getShift().name())
                        .capacityAllocated(a.getCapacityAllocated())
                        .build())
                .collect(Collectors.toList());
    }

    @Value("${app.upload.result-dir}")
    private String resultDir;

    @Override
    @Transactional
    public String uploadWholeGroupResult(Long requestId,
                                         Long doctorId,
                                         MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Đảm bảo request tồn tại (cũng để sau này cần thêm info)
        GroupHealthRequest req = groupHealthRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        // 🔹 Thư mục gốc lấy từ cấu hình app.upload.result-dir
        Path uploadDirPath = Paths.get(resultDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadDirPath); // tạo nếu chưa có

        // 🔹 Tên file chuẩn hóa
        String fileName = String.format(
                "request-%d-doctor-%d-result.xlsx",
                requestId, doctorId
        );

        Path targetPath = uploadDirPath.resolve(fileName);

        // Ghi file
        file.transferTo(targetPath.toFile());

        // 🔹 Tìm các assignment của doctor này cho request này
        // (nếu bạn chưa có method này thì tạo trong repository:
        // List<GroupHealthAssignment> findByRequestIdAndDoctorId(Long requestId, Long doctorId);)
        List<GroupHealthAssignment> doctorAssignments =
                assignmentRepository.findByRequestIdAndDoctorId(requestId, doctorId);

        if (doctorAssignments.isEmpty()) {
            // Không bắt buộc phải quăng lỗi, tuỳ bạn
            // nhưng nên log để biết có vấn đề mapping
            log.warn("No assignments found for request {} and doctor {}", requestId, doctorId);
        } else {
            for (GroupHealthAssignment a : doctorAssignments) {
                a.setResultStatus(ResultStatus.UPLOADED);
                // Nếu entity có field lưu path file thì set luôn, ví dụ:
                // a.setResultFilePath(targetPath.toString());
            }
            assignmentRepository.saveAll(doctorAssignments);
        }

        log.info("✅ Saved WHOLE result file for doctor {} request {} at {}",
                doctorId, requestId, targetPath);

        // cho controller dùng nếu cần
        return targetPath.toString();
    }

    @Override
    @Transactional
    public String uploadResultFile(Long requestId,
                                   int partIndex,
                                   Long doctorId,
                                   MultipartFile file) throws Exception {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        GroupHealthRequest req = groupHealthRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        // 🔹 Lấy đường dẫn file gốc rồi chuẩn hoá thành absolute path
        Path originalPath = Paths.get(req.getExcelFilePath()).toAbsolutePath();
        Path baseDir = originalPath.getParent();

        // Fallback nếu excelFilePath null / lỗi
        if (baseDir == null) {
            baseDir = Paths.get("uploads", "group-health", String.valueOf(requestId)).toAbsolutePath();
        }

        // 🔹 Thư mục chứa file result
        Path resultsDir = baseDir.resolve("results");
        Files.createDirectories(resultsDir);     // đảm bảo tồn tại

        String filename = String.format(
                "request-%d-part-%d-doctor-%d-result.xlsx",
                requestId, partIndex, doctorId
        );
        Path dest = resultsDir.resolve(filename);

        // 🔹 Ghi file bằng NIO
        try (var in = file.getInputStream()) {
            Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // TODO: nếu muốn đánh dấu trạng thái "DONE" cho assignment
        // có thể query GroupHealthAssignment rồi set status ở đây.

        log.info("✅ Saved result file for doctor {} request {} part {} at {}",
                doctorId, requestId, partIndex, dest);

        return dest.toString();
    }

}
