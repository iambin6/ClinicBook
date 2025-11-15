package com.nano.clinicbooking.service.grouphealth;

import com.nano.clinicbooking.dto.request.groupheath.GroupApproveRequest;
import com.nano.clinicbooking.dto.request.groupheath.GroupAssignmentItem;
import com.nano.clinicbooking.dto.response.groupheath.GroupApprovalSummaryResponse;
import com.nano.clinicbooking.dto.response.groupheath.GroupAssignmentView;
import com.nano.clinicbooking.dto.response.groupheath.GroupHealthSummaryResponse;
import com.nano.clinicbooking.enums.ResultStatus;
import com.nano.clinicbooking.enums.ShiftStatus;
import com.nano.clinicbooking.model.Doctor;
import com.nano.clinicbooking.model.DoctorShift;
import com.nano.clinicbooking.model.groupheath.GroupHealthAssignment;
import com.nano.clinicbooking.model.groupheath.GroupHealthRequest;
import com.nano.clinicbooking.repository.grouphealth.GroupHealthRequestRepository;
import com.nano.clinicbooking.repository.SpecialtyRepository;
import com.nano.clinicbooking.repository.appointment.AppointmentRepository;
import com.nano.clinicbooking.repository.doctor.DoctorRepository;
import com.nano.clinicbooking.repository.doctor.DoctorShiftRepository;
import com.nano.clinicbooking.repository.grouphealth.GroupHealthAssignmentRepository;
import com.nano.clinicbooking.service.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupHealthApprovalServiceImpl implements GroupHealthApprovalService {

    private final GroupHealthRequestRepository groupHealthRequestRepository;
    private final SpecialtyRepository specialtyRepository; // (chưa dùng ở bản này, có thể giữ)
    private final DoctorRepository doctorRepository;
    private final DoctorShiftRepository doctorShiftRepository;
    private final AppointmentRepository appointmentRepository;
    private final GroupHealthAssignmentRepository assignmentRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public GroupApprovalSummaryResponse approve(Long requestId, GroupApproveRequest requestBody) {
        GroupHealthRequest req = groupHealthRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (req.getStatus() != GroupHealthRequest.RequestStatus.PENDING) {
            throw new IllegalStateException("Request must be PENDING to approve");
        }

        List<GroupAssignmentItem> items = Optional.ofNullable(requestBody.getAssignments())
                .orElse(Collections.emptyList());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("assignments must not be empty");
        }

        List<GroupHealthAssignment> toSave = new ArrayList<>();

        for (GroupAssignmentItem it : items) {
            if (it.getDoctorId() == null || it.getShiftId() == null) {
                throw new IllegalArgumentException("doctorId/shiftId must not be null");
            }

            Doctor doctor = doctorRepository.findById(it.getDoctorId())
                    .orElseThrow(() -> new IllegalArgumentException("Doctor not found: " + it.getDoctorId()));
            DoctorShift shift = doctorShiftRepository.findById(it.getShiftId())
                    .orElseThrow(() -> new IllegalArgumentException("Shift not found: " + it.getShiftId()));

            if (shift.getStatus() != ShiftStatus.ACTIVE) {
                throw new IllegalArgumentException("Shift must be ACTIVE");
            }
            if (!Objects.equals(shift.getDoctor().getId(), doctor.getId())) {
                throw new IllegalArgumentException("Shift does not belong to the given doctor");
            }
            if (req.getPreferredDate() != null && shift.getDate() != null
                    && !shift.getDate().equals(req.getPreferredDate())) {
                throw new IllegalArgumentException("Shift date must equal request preferredDate");
            }

            // 👉 Group health không chia capacity, ta set mặc định = 1 cho đỡ null
            GroupHealthAssignment a = GroupHealthAssignment.builder()
                    .request(req)
                    .specialty(doctor.getSpecialty())
                    .doctor(doctor)
                    .shift(shift)
                    .capacityAllocated(1)
                    .note(requestBody.getNote())
                    .resultStatus(ResultStatus.NONE)  // nếu muốn default
                    .build();
            toSave.add(a);
        }

        // Lưu assignments & cập nhật trạng thái request
        assignmentRepository.saveAll(toSave);

        req.setStatus(GroupHealthRequest.RequestStatus.APPROVED);
        groupHealthRequestRepository.save(req);

        // Notify (best-effort)
        try {
            notificationService.notifyUserRequestApproved(req);
            toSave.stream()
                    .map(a -> a.getDoctor().getId())
                    .distinct()
                    .forEach(doctorId -> notificationService.notifyDoctorAssigned(doctorId, req));
        } catch (Exception ex) {
            log.warn("Notify failed: {}", ex.getMessage());
        }

        // Sau khi save assignments
        Set<DoctorShift> usedShifts = toSave.stream()
                .map(GroupHealthAssignment::getShift)
                .collect(Collectors.toSet());

        for (DoctorShift shift : usedShifts) {
            shift.setStatus(ShiftStatus.GROUP_BLOCKED);
        }
        doctorShiftRepository.saveAll(usedShifts);


        // Build response
        List<GroupAssignmentView> views = toSave.stream()
                .map(a -> GroupAssignmentView.builder()
                        .assignmentId(a.getId())
                        .specialtyId(a.getSpecialty().getId())
                        .specialtyName(a.getSpecialty().getName())
                        .doctorId(a.getDoctor().getId())
                        .doctorName(a.getDoctor().getFullName())
                        .shiftId(a.getShift().getId())
                        .shiftDate(a.getShift().getDate())
                        .shiftType(a.getShift().getShift().name())
                        .capacityAllocated(a.getCapacityAllocated()) // = 1
                        .note(a.getNote())
                        .build())
                .collect(Collectors.toList());

        return GroupApprovalSummaryResponse.builder()
                .requestId(req.getId())
                .status(req.getStatus().name())
                .groupName(req.getGroupName())
                .departments(req.getDepartments())
                .preferredDate(req.getPreferredDate())
                .assignments(views)
                .build();
    }


    // 🆕 TỪ CHỐI YÊU CẦU
    @Override
    @Transactional
    public GroupApprovalSummaryResponse reject(Long requestId, String reason) {
        GroupHealthRequest req = groupHealthRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (req.getStatus() != GroupHealthRequest.RequestStatus.PENDING)
            throw new IllegalStateException("Only PENDING requests can be rejected");

        req.setStatus(GroupHealthRequest.RequestStatus.REJECTED);
        groupHealthRequestRepository.save(req);

        try {
            notificationService.notifyUserRequestRejected(req, reason);
        } catch (Exception e) {
            log.warn("Notify failed: {}", e.getMessage());
        }

        return GroupApprovalSummaryResponse.builder()
                .requestId(req.getId())
                .status(req.getStatus().name())
                .groupName(req.getGroupName())
                .departments(req.getDepartments())
                .preferredDate(req.getPreferredDate())
                .assignments(Collections.emptyList())
                .build();
    }

    @Transactional
    public GroupApprovalSummaryResponse approveResultsAndSendToCustomer(Long requestId) throws Exception {
        GroupHealthRequest req = groupHealthRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        List<GroupHealthAssignment> assignments =
                assignmentRepository.findByRequestId(requestId);

        // Lọc những assignment đã upload
        List<GroupHealthAssignment> uploaded = assignments.stream()
                .filter(a -> a.getResultStatus() == ResultStatus.UPLOADED)
                .collect(Collectors.toList());

        if (uploaded.isEmpty()) {
            throw new IllegalStateException("No uploaded results to approve");
        }

        // ✅ Duyệt từng assignment
        for (GroupHealthAssignment a : uploaded) {
            a.setResultStatus(ResultStatus.APPROVED);
        }
        assignmentRepository.saveAll(uploaded);

        // ✅ Có thể zip toàn bộ file result nếu bạn muốn (tùy)
        Path resultDir = Path.of(req.getExcelFilePath()).getParent().resolve("results");
        Path zipPath = resultDir.resolve("group-" + req.getId() + "-final-results.zip");

        File zipFile = zipPath.toFile();
        FileUtils.deleteQuietly(zipFile);
        FileUtils.write(zipFile, ""); // TODO: sau này thay bằng nén thật sự

        // ✅ Đánh dấu request hoàn thành
        req.setStatus(GroupHealthRequest.RequestStatus.COMPLETED);
        groupHealthRequestRepository.save(req);

        // ✅ Gửi mail cho khách: TẠM THỜI COMMENT – để bạn của bạn code
        notificationService.notifyUserResultsSent(req, zipPath.toString());
        notificationService.notifyMentorResultsApproved(req);

        // Ở đây mình không cần trả assignments chi tiết nữa, có thể trả list rỗng
        return GroupApprovalSummaryResponse.builder()
                .requestId(req.getId())
                .status(req.getStatus().name())
                .groupName(req.getGroupName())
                .departments(req.getDepartments())
                .preferredDate(req.getPreferredDate())
                .assignments(Collections.emptyList())
                .build();
    }


    // ===================== LISTING (đã fix enum + createdAt) =====================
    @Override
    public List<GroupHealthSummaryResponse> getAllRequests(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<GroupHealthRequest> requests;

        if (status != null && !status.isBlank()) {
            GroupHealthRequest.RequestStatus st =
                    GroupHealthRequest.RequestStatus.valueOf(status.trim().toUpperCase());
            requests = groupHealthRequestRepository.findByStatus(st, pageable);
        } else {
            requests = groupHealthRequestRepository.findAll(pageable);
        }

        return requests.getContent()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    private GroupHealthSummaryResponse toSummary(GroupHealthRequest req) {
        return GroupHealthSummaryResponse.builder()
                .id(req.getId())
                .groupName(req.getGroupName())
                .phoneNumber(req.getPhoneNumber())
                .preferredDate(req.getPreferredDate())
                .status(req.getStatus().name())
                .createdAt(req.getCreatedAt())
                .build();
    }

}
