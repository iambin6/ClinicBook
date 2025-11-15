package com.nano.clinicbooking.service.email.sent_forgotpassword;

import com.nano.clinicbooking.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ForgotPasswordEmailContentService {

    @Value("${clinic.name:ClinicBooking}")
    private String clinicName;

    /**
     * Tạo nội dung email quên mật khẩu (khi hệ thống tạo mật khẩu mới)
     */
    public String buildForgotPasswordEmailHtml(User user, String newPassword) {

        String name = safe(user.getFullName());
        String email = safe(user.getEmail());

        return String.format("""
                    <div style="font-family:Arial,Helvetica,sans-serif;color:#111;line-height:1.6;max-width:640px;margin:auto;">
                
                      <div style="padding:20px 16px;text-align:center;border-bottom:1px solid #eee;">
                        <div style="font-size:20px;font-weight:700;">%s</div>
                        <div style="font-size:12px;color:#666;">Khôi phục mật khẩu tài khoản</div>
                      </div>
                
                      <div style="padding:20px 16px;">
                        <h3>Xin chào %s,</h3>
                
                        <p>Bạn vừa yêu cầu <b>khôi phục mật khẩu</b> cho tài khoản tại phòng khám <b>%s</b>.</p>
                
                        <p>Dưới đây là mật khẩu mới của bạn:</p>
                
                        <div style="border:1px solid #eee;border-radius:12px;padding:14px 18px;margin:18px 0;background:#fafafa;">
                            <div style="font-size:14px;color:#555;margin-bottom:8px;">Mật khẩu mới</div>
                            <div style="font-size:16px;font-weight:700;color:#000;">%s</div>
                        </div>
              
                        <p style="font-size:13px;color:#666;">
                          Vui lòng đăng nhập và thay đổi mật khẩu ngay để đảm bảo an toàn cho tài khoản.
                        </p>
                
                        <div style="margin-top:20px;font-size:13px;color:#444;">
                          <b>Email đăng nhập:</b> %s
                        </div>
                
                        <p style="margin-top:20px;font-size:13px;color:#666;">
                          Nếu bạn không thực hiện yêu cầu này, vui lòng liên hệ ngay với phòng khám để được hỗ trợ.
                        </p>
                
                        <p>Trân trọng,<br><b>Đội ngũ %s</b></p>
                      </div>
                
                      <div style="border-top:1px solid #eee;padding:12px;text-align:center;font-size:12px;color:#777;">
                        Email tự động – vui lòng không trả lời.
                      </div>
                
                    </div>
""", clinicName, name, clinicName, newPassword, email, clinicName);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s.trim();
    }
}