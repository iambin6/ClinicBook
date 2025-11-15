package com.nano.clinicbooking.service.email.send_register;

import com.nano.clinicbooking.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiEmailService {

    private final EmailService emailService;
    private final RegisterEmailContentService registerContent;

    // Gửi email đăng ký thành công – CHỈ cho bệnh nhân (Patient)
    public void sendRegisterSuccessEmail(User user) {

        if (user == null || user.getEmail() == null) {
            throw new IllegalArgumentException("Thiếu thông tin người nhận email");
        }

        // ❗ Chỉ gửi email cho bệnh nhân
        if (!"Patient".equalsIgnoreCase(user.getUserType())) {
            return;  // nếu không phải bệnh nhân → không gửi email
        }

        String subject = "Đăng ký thành công - ClinicBooking";
        String html = registerContent.generateRegisterSuccessEmail(user);

        emailService.sendToUser(user, subject, html, true);
        System.out.printf("Đã gửi email đăng ký thành công cho %s (%s)%n",
                user.getFullName(), user.getEmail());
    }
}