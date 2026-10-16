package com.nano.clinicbooking.service.email.send_register;

import com.nano.clinicbooking.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEmailService {

    private final EmailService emailService;
    private final RegisterEmailContentService registerContent;

    // Gửi email đăng ký thành công – CHỈ cho bệnh nhân (Patient)
    public void sendRegisterSuccessEmail(User user) {

        if (user == null || user.getEmail() == null) {
            log.warn("Thiếu thông tin người nhận email đăng ký");
            return;
        }

        // ❗ Chỉ gửi email cho bệnh nhân
        if (!"Patient".equalsIgnoreCase(user.getUserType())) {
            return;  // nếu không phải bệnh nhân → không gửi email
        }

        // Chạy bất đồng bộ để không chặn luồng đăng ký của người dùng
        CompletableFuture.runAsync(() -> {
            try {
                String subject = "Đăng ký thành công - ClinicBooking";
                String html = registerContent.generateRegisterSuccessEmail(user);

                emailService.sendToUser(user, subject, html, true);
                log.info("Đã gửi email đăng ký thành công cho {} ({})",
                        user.getFullName(), user.getEmail());
            } catch (Exception e) {
                log.error("Không thể gửi email đăng ký cho {}: {}", user.getEmail(), e.getMessage());
            }
        });
    }
}