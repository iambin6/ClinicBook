package com.nano.clinicbooking.service.email.sent_forgotpassword;

import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.service.email.send_register.EmailService;

import org.springframework.stereotype.Service;

@Service

public class ForgotPasswordEmailService {

    private final EmailService emailService; // giống EventAnnouncer
    private final ForgotPasswordEmailContentService contentService;

    public ForgotPasswordEmailService(EmailService emailService, ForgotPasswordEmailContentService contentService) {
        this.emailService = emailService;
        this.contentService = contentService;
    }

    /**
     * Gửi email cho một người (Patient)
     */
    public void send(User user, String newPassword) {

        if (user == null || user.getEmail() == null) {
            throw new IllegalArgumentException("Không có thông tin người nhận email");
        }

        // ❌ Không phải Patient → không gửi mail
        if (!"Patient".equalsIgnoreCase(user.getUserType())) {
            return;
        }

        String subject = "Khôi phục mật khẩu tài khoản";
        String html = contentService.buildForgotPasswordEmailHtml(user, newPassword);

        emailService.sendToUser(user, subject, html, true);
    }
}