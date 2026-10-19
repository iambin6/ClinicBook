package com.nano.clinicbooking.service.email.sent_appointment;

import com.nano.clinicbooking.dto.response.AppointmentDto;
import com.nano.clinicbooking.model.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class AppointmentEmailContentService implements AppointmentEmailContentProvider {

    @Value("${clinic.name:ClinicBooking}")
    private String clinicName;

    /**
     * Xây dựng nội dung HTML của email thông báo lịch hẹn.
     * @param patient Bệnh nhân (kiểu User)
     * @param app     Dữ liệu lịch hẹn
     * @param subject Tiêu đề email
     */
    public String buildAppointmentEmailHtml(User patient, AppointmentDto app, String subject) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("vi", "VN"));
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm", new Locale("vi", "VN"));

        String date = app.getAppointmentDate() != null ? app.getAppointmentDate().format(dateFmt) : "—";
        String time = app.getAppointmentTime() != null ? app.getAppointmentTime().format(timeFmt) : "—";

        String name = safe(patient.getFullName());
        String email = safe(patient.getEmail());
        String doctor = safe(app.getDoctorName());
        String specialty = safe(app.getSpecialtyName());
        String reason = safe(app.getReason());
        String status = app.getStatus() != null ? app.getStatus().name() : "—";
        String subj = (subject == null || subject.isBlank()) ? "Thông báo lịch hẹn khám bệnh" : subject;

        return buildFallbackHtml(safe(clinicName), safe(subj), name, date, time, doctor, specialty, reason, status, email);
    }

    /** Deterministic booking receipt; delivery does not depend on an AI provider. */
    private String buildFallbackHtml(String clinicName, String subj, String name, String date, String time,
                                     String doctor, String specialty, String reason, String status, String email) {
        return String.format("""
            <div style="font-family:Arial,Helvetica,sans-serif;color:#111;line-height:1.6;max-width:640px;margin:auto;">
              <div style="display:none;visibility:hidden;opacity:0;height:0;overflow:hidden;color:transparent;">%s</div>
              <div style="padding:20px 16px;text-align:center;border-bottom:1px solid #eee;">
                <div style="font-size:20px;font-weight:700;">%s</div>
                <div style="font-size:12px;color:#666;">Thông báo lịch hẹn</div>
              </div>
              <div style="padding:20px 16px;">
                <h3>Xin chào %s,</h3>
                <p>Bạn có lịch hẹn khám tại phòng khám <b>%s</b>.</p>
                <p><b>Ngày:</b> %s<br><b>Giờ:</b> %s<br><b>Bác sĩ:</b> %s<br><b>Chuyên khoa:</b> %s</p>
                <p><b>Lý do khám:</b> %s</p>
                <p><b>Trạng thái:</b> %s</p>
                <div style="border:1px solid #eee;border-radius:12px;padding:12px;margin:16px 0;background:#fafafa;">
                  <div style="font-size:14px;color:#555;margin-bottom:8px;">Tài khoản</div>
                  <div style="font-size:14px;"><b>Email:</b> %s</div>
                </div>
                <p style="font-size:13px;color:#666;">Nếu cần hỗ trợ, vui lòng liên hệ phòng khám.</p>
                <p>Trân trọng,<br><b>Đội ngũ %s</b></p>
              </div>
              <div style="border-top:1px solid #eee;padding:12px;text-align:center;font-size:12px;color:#777;">
                Email tự động – vui lòng không trả lời.
              </div>
            </div>
        """, subj, clinicName, name, clinicName, date, time, doctor, specialty, reason, status, email, clinicName);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
