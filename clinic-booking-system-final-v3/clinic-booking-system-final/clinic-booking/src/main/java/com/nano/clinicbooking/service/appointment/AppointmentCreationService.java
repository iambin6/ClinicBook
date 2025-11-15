package com.nano.clinicbooking.service.appointment;

import com.nano.clinicbooking.dto.request.BookAppointmentRequest;
import com.nano.clinicbooking.enums.AppointmentStatus;
import com.nano.clinicbooking.enums.AppointmentType;
import com.nano.clinicbooking.enums.SlotStatus;
import com.nano.clinicbooking.exception.ResourceNotFoundException;
import com.nano.clinicbooking.mapper.AppointmentMapper;
import com.nano.clinicbooking.model.*;
import com.nano.clinicbooking.repository.SpecialtyRepository;
import com.nano.clinicbooking.repository.appointment.AppointmentRepository;
import com.nano.clinicbooking.repository.doctor.DoctorRepository;
import com.nano.clinicbooking.repository.doctor.DoctorShiftRepository;
import com.nano.clinicbooking.repository.patient_info.PatientInformationRepository;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import com.nano.clinicbooking.repository.shiftSlot.ShiftSlotRepository;
import com.nano.clinicbooking.service.payment.VnpayConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentCreationService {

    private final AppointmentRepository appointmentRepo;
    private final UserRepository userRepo;
    private final DoctorRepository doctorRepo;
    private final SpecialtyRepository specialtyRepo;
    private final DoctorShiftRepository shiftRepo;
    private final ShiftSlotRepository slotRepo;
    private final PatientInformationRepository patientInfoRepo;
    private final AppointmentMapper appointmentMapper;
    private final SimpMessagingTemplate messagingTemplate;


    @Transactional
    public Appointment createAppointment(BookAppointmentRequest request, Long patientId, Long doctorId, Long specialtyId) {

        // 🔹 1. Lấy thông tin bệnh nhân & chuyên khoa
        User patient = userRepo.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        Specialty specialty = specialtyRepo.findById(specialtyId)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty not found"));

        // 🔹 2. Khởi tạo Appointment
        Appointment reqApp = request.getAppointment();
        Appointment appointment = new Appointment();

        appointment.setPatient(patient);
        appointment.setSpecialty(specialty);
        appointment.setStatus(AppointmentStatus.PENDING_CONFIRMATION);

        // ✅ Sinh mã cuộc hẹn duy nhất — tránh trùng
        String prefix = (reqApp != null && reqApp.getType() == AppointmentType.ONLINE) ? "ONL" : "OFF";
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        appointment.setAppointmentNo(prefix + unique);

        // 🔹 3. Gán reason và type
        if (reqApp != null) {
            appointment.setReason(reqApp.getReason());
            appointment.setType(reqApp.getType() != null ? reqApp.getType() : AppointmentType.OFFLINE);
        } else {
            appointment.setType(AppointmentType.OFFLINE);
        }

        // 🔹 4. Gán bác sĩ
        if (doctorId != null) {
            Doctor doctor = doctorRepo.findById(doctorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
            appointment.setDoctor(doctor);
        }

        // 🔹 5. Gán ca khám và slot (tính theo từng slot riêng)
        if (request.getShiftId() != null) {
            DoctorShift shift = shiftRepo.findById(request.getShiftId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

            int maxPerSlot = shift.getMaxPatients();

            // ✅ Lấy toàn bộ slot của ca
            List<ShiftSlot> slots = slotRepo.findByShiftIdOrderBySlotNumberAsc(shift.getId());
            if (slots.isEmpty()) {
                throw new IllegalStateException("Ca khám này chưa có slot nào!");
            }

            // ✅ Tính số bệnh nhân của cuộc hẹn mới
            int newPatients = (request.getPatients() == null ? 1 : request.getPatients().size() + 1);

            // ✅ Tìm slot đầu tiên còn chỗ trống
            ShiftSlot targetSlot = null;
            for (ShiftSlot s : slots) {
                // Đếm tổng số bệnh nhân trong slot này
                long currentPatients = appointmentRepo.findAll().stream()
                        .filter(a -> a.getSlot() != null && Objects.equals(a.getSlot().getId(), s.getId()))
                        .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                        .mapToLong(a -> a.getPatientInfos().size() + 1)
                        .sum();

                if (currentPatients + newPatients <= maxPerSlot) {
                    targetSlot = s;
                    break;
                }
            }

            if (targetSlot == null) {
                throw new IllegalStateException(
                        "❌ Tất cả slot trong ca đã đủ " + maxPerSlot + " bệnh nhân mỗi slot. " +
                                "Vui lòng chọn ca khác hoặc giảm số lượng bệnh nhân."
                );
            }

            // ✅ Gán slot này cho appointment
            targetSlot.setStatus(SlotStatus.BOOKED);
            targetSlot.setAppointment(appointment);
            slotRepo.save(targetSlot);

            appointment.setShift(shift);
            appointment.setSlot(targetSlot);
            appointment.setAppointmentDate(shift.getDate());
            appointment.setAppointmentTime(targetSlot.getStartTime());
        }


        // 🔹 6. Gắn danh sách bệnh nhân phụ
        if (request.getPatients() != null && !request.getPatients().isEmpty()) {
            List<PatientInformation> infos = request.getPatients().stream()
                    .map(i -> mapPatientInfo(i, appointment, patient))
                    .collect(Collectors.toList());
            appointment.setPatientInfos(infos);
        }

        // 🔹 7. Nếu là ONLINE → tạo link thanh toán VNPAY
        if (appointment.getType() == AppointmentType.ONLINE) {
            String txnRef = appointment.getAppointmentNo();
            long amount = (long) (appointment.getDoctor().getConsultationFee() * 100);

            // ✅ Dùng config có sẵn, fallback nếu không có request
            Map<String, String> vnp_Params = VnpayConfig.initParams(txnRef, amount, null);
            vnp_Params.put("vnp_IpAddr", "127.0.0.1");

            // ✅ Encode trước khi hash (theo chuẩn VNPAY)
            List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
            Collections.sort(fieldNames);

            StringBuilder hashData = new StringBuilder();
            StringBuilder query = new StringBuilder();

            for (String fieldName : fieldNames) {
                String fieldValue = vnp_Params.get(fieldName);
                if (fieldValue != null && !fieldValue.isEmpty()) {
                    if (hashData.length() > 0) hashData.append('&');

                    // 🔥 Encode giá trị trước khi hash (rất quan trọng!)
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII).replace("+", "%20");

                    hashData.append(fieldName).append('=').append(encodedValue);

                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                            .append('=')
                            .append(encodedValue)
                            .append('&');
                }
            }

            // ✅ Tạo chữ ký
            String secureHash = VnpayConfig.hmacSHA512(VnpayConfig.secretKey, hashData.toString());

            // ✅ Hoàn thành URL
            String paymentUrl = VnpayConfig.buildPaymentUrl(vnp_Params);
            appointment.setMeetingUrl(paymentUrl);


            // ✅ Log ra để kiểm tra
            System.out.println("💰 Doctor fee = " + appointment.getDoctor().getConsultationFee());
            System.out.println("💰 Final vnp_Amount = " + vnp_Params.get("vnp_Amount"));
            System.out.println("🔑 HASH DATA = " + hashData);
            System.out.println("🔑 SIGNATURE = " + secureHash);
            System.out.println("✅ FINAL VNPAY URL = " + paymentUrl);

            // ✅ Lưu link thanh toán
            appointment.setMeetingUrl(paymentUrl);
            appointment.setJoinCode(UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        }




        // 🔹 8. Lưu vào DB
        Appointment saved = appointmentRepo.save(appointment);

        System.out.println("✅ Created appointmentNo = " + saved.getAppointmentNo()
                + " | Type = " + saved.getType()
                + " | Status = " + saved.getStatus());

        // ✅ 🔥 Gửi thông báo real-time cho lễ tân
        try {
            String msg = "🩺 Bệnh nhân " + patient.getFullName()
                    + " vừa đặt lịch tại khoa " + specialty.getName()
                    + " (" + saved.getAppointmentDate() + " - " + saved.getAppointmentTime() + ")";
            messagingTemplate.convertAndSend("/topic/reception", msg);
        } catch (Exception e) {
            System.out.println("⚠️ WebSocket send failed: " + e.getMessage());
        }

        return saved;
    }

    private PatientInformation mapPatientInfo(PatientInformation info, Appointment appointment, User owner) {
        PatientInformation entity;

        if (info.getId() != null) {
            entity = patientInfoRepo.findById(info.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient info not found"));
        } else {
            entity = new PatientInformation();
            entity.setFullName(info.getFullName());
            entity.setGender(info.getGender());
            entity.setPhoneNumber(info.getPhoneNumber());
            entity.setAddress(info.getAddress());
            entity.setDob(info.getDob());
            entity.setRelationship(info.getRelationship());
            entity.setOwner(owner);
        }

        entity.setAppointment(appointment);
        return entity;
    }
}
