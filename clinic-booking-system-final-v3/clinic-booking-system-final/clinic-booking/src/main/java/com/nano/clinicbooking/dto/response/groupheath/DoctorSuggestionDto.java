package com.nano.clinicbooking.dto.response.groupheath;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

// dùng riêng cho logic chọn bác sĩ trong khoa đc chọn cơ bản thôi
// DoctorSuggestionDto sẽ gọn hơn, chỉ chứa thông tin cần thiết để hiển thị danh sách đề xuất.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorSuggestionDto {
    private Long doctorId;
    private String doctorName;
    private String specialtyName;
    private Long shiftId;
    private LocalDate shiftDate;
    private String shiftType;
    private Integer maxPatients;
    private Integer slotsAvailable;
    private Double usage; // 🆕 0.0 = rảnh hoàn toàn, 1.0 = full
}
