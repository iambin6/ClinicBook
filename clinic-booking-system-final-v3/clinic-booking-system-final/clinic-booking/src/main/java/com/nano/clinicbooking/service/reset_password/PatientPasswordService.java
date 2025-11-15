package com.nano.clinicbooking.service.reset_password;

import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PatientPasswordService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PatientPasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    public String changePassword(String email,
                                 String oldPassword,
                                 String newPassword,
                                 String confirmPassword) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        //  Chỉ Patient đổi được
        if (!"Patient".equalsIgnoreCase(user.getUserType())) {
            return "Only patients can change password";
        }

        // Check mật khẩu cũ
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return "Old password is incorrect";
        }

        // Check confirm password
        if (!newPassword.equals(confirmPassword)) {
            return "Confirm password does not match";
        }

        // Update mật khẩu
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return "Password changed successfully";
    }

}
