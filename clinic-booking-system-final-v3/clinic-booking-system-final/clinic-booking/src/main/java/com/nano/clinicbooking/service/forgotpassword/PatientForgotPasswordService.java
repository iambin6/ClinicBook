package com.nano.clinicbooking.service.forgotpassword;

import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import com.nano.clinicbooking.service.email.sent_forgotpassword.ForgotPasswordEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class PatientForgotPasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ForgotPasswordEmailService forgotPasswordEmailService;
    /**
     * Quên mật khẩu chỉ cho Patient
     * -> tạo mật khẩu mới random
     * -> lưu thẳng vào bảng users
     */
    public String resetPasswordForPatient(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại"));

        if (!"Patient".equalsIgnoreCase(user.getUserType())) {
            throw new RuntimeException("Chỉ tài khoản Patient được dùng chức năng quên mật khẩu");
        }

        // Tạo mật khẩu mới
        String newPassword = generateRandomPassword(8);

        // Mã hoá + lưu
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        //  GỬI EMAIL Ở ĐÂY
        forgotPasswordEmailService.send(user, newPassword);
        return newPassword;
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}