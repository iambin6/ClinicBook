package com.nano.clinicbooking.service.grouphealth;

import com.nano.clinicbooking.dto.response.groupheath.DoctorSuggestionDto;
import com.nano.clinicbooking.dto.response.groupheath.GroupHealthAnalysisResponse;
import com.nano.clinicbooking.dto.response.groupheath.GroupHealthMemberInfo;
import com.nano.clinicbooking.enums.ShiftStatus;
import com.nano.clinicbooking.model.Doctor;
import com.nano.clinicbooking.model.DoctorShift;
import com.nano.clinicbooking.model.Specialty;
import com.nano.clinicbooking.model.groupheath.GroupHealthRequest;
import com.nano.clinicbooking.repository.SpecialtyRepository;
import com.nano.clinicbooking.repository.appointment.AppointmentRepository;
import com.nano.clinicbooking.repository.doctor.DoctorRepository;
import com.nano.clinicbooking.repository.doctor.DoctorShiftRepository;
import com.nano.clinicbooking.repository.grouphealth.GroupHealthRequestRepository;
import com.nano.clinicbooking.utils.ExcelParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupHealthAnalysisServiceImpl implements GroupHealthAnalysisService {

    private final GroupHealthRequestRepository groupHealthRequestRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorShiftRepository doctorShiftRepository;
    private final AppointmentRepository appointmentRepository;

    @Override
    public GroupHealthAnalysisResponse analyze(Long requestId) throws IOException {
        GroupHealthRequest request = groupHealthRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        // ✅ 1. Đọc Excel
        Path excelPath = Paths.get(request.getExcelFilePath());
        List<GroupHealthMemberInfo> members = ExcelParser.parseMembers(excelPath.toString());

        // ✅ 2. Xác định chuyên khoa
        List<String> departmentNames = Arrays.stream(request.getDepartments().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<Specialty> specialties = specialtyRepository.findByNameIn(departmentNames);
        List<Long> specialtyIds = specialties.stream()
                .map(Specialty::getId)
                .collect(Collectors.toList());

        // ✅ 3. Gợi ý bác sĩ
        List<DoctorSuggestionDto> suggestions = new ArrayList<>();

        for (Long sid : specialtyIds) {
            List<Doctor> doctors = doctorRepository.findBySpecialtyId(sid);
            log.info("🔍 Specialty {} có {} bác sĩ.", sid, doctors.size());

            for (Doctor doctor : doctors) {
                log.info("👨‍⚕️ Xét bác sĩ: {} (ID={}) - chuyên khoa: {}",
                        doctor.getFullName(), doctor.getId(), doctor.getSpecialty().getName());

                List<DoctorShift> shifts = doctorShiftRepository.findByDoctorIdAndDate(
                        doctor.getId(), request.getPreferredDate());
                log.info("🗓️ Bác sĩ {} có {} ca trực vào ngày {}",
                        doctor.getFullName(), shifts.size(), request.getPreferredDate());

                for (DoctorShift shift : shifts) {
                    long booked = appointmentRepository.countByShiftId(shift.getId());
                    int max = shift.getMaxPatients() != null ? shift.getMaxPatients() : 0;
                    int available = max > 0 ? Math.max(0, max - (int) booked) : 0;

                    log.info("➡️ Shift {} [{}] - status={}, maxPatients={}, booked={}, available={}",
                            shift.getId(), shift.getShift(), shift.getStatus(),
                            max, booked, available);

                    if (shift.getStatus() != ShiftStatus.ACTIVE) continue;
                    if (available <= 0) continue;

                    double usage = 0.0;
                    if (max > 0) {
                        usage = (double) booked / max; // 0.0 = rảnh, 1.0 = full
                    }

                    suggestions.add(DoctorSuggestionDto.builder()
                            .doctorId(doctor.getId())
                            .doctorName(doctor.getFullName())
                            .specialtyName(doctor.getSpecialty().getName())
                            .shiftId(shift.getId())
                            .shiftDate(shift.getDate())
                            .shiftType(shift.getShift().name())
                            .maxPatients(max)
                            .slotsAvailable(available)
                            .usage(usage)
                            .build());

                    log.info("✅ Gợi ý bác sĩ {} - ca {} ({}) còn {} slot trống, usage={}.",
                            doctor.getFullName(), shift.getId(), shift.getShift(), available, usage);
                }
            }
        }

        // ✨ Sort theo mức độ rảnh (usage tăng dần)
        suggestions.sort(Comparator.comparing(DoctorSuggestionDto::getUsage));

        log.info("📊 Tổng số gợi ý bác sĩ được tạo (sau khi sort): {}", suggestions.size());

        return GroupHealthAnalysisResponse.builder()
                .requestId(request.getId())
                .groupName(request.getGroupName())
                .departments(request.getDepartments())
                .preferredDate(request.getPreferredDate())
                .totalMembers(members.size())
                .members(members)
                .suggestedDoctors(suggestions)
                .build();
    }
}
