package com.nano.clinicbooking.controller.login;

import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import com.nano.clinicbooking.dto.request.LoginRequest;
import com.nano.clinicbooking.dto.request.RegistrationRequest;
import com.nano.clinicbooking.dto.response.ApiResponse;
import com.nano.clinicbooking.security.JwtService;
import com.nano.clinicbooking.dto.request.ChangePasswordRequest;
import com.nano.clinicbooking.service.email.send_register.AiEmailService;
import com.nano.clinicbooking.service.reset_password.PatientPasswordService;
import com.nano.clinicbooking.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final PatientPasswordService passwordService;
    // 🟢 1️⃣ Đăng ký bệnh nhân (Public)
    // Mặc định userType = "Patient"
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerPatient(@RequestBody RegistrationRequest request) {
        try {
            request.setUserType("Patient");
            User user = userService.register(request);
            return ResponseEntity.ok(new ApiResponse("Patient registered successfully", user));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    // 🟣 2️⃣ Đăng ký admin (Public - dùng tạm để tạo admin đầu tiên)
    // Sau khi có admin rồi, nên giới hạn chỉ Admin mới gọi API này
    @PostMapping("/register-admin")
    public ResponseEntity<ApiResponse> registerAdmin(@RequestBody RegistrationRequest request) {
        try {
            request.setUserType("Admin");
            User admin = userService.register(request);
            return ResponseEntity.ok(new ApiResponse("Admin registered successfully", admin));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    // 🟡 3️⃣ Đăng nhập (Public)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@RequestBody LoginRequest login) {
        try {
            // 1️⃣ Tìm user theo email
            User user = userRepository.findByEmail(login.getEmail())
                    .orElseThrow(() -> new RuntimeException("Invalid email or password"));

            // 2) Nếu tài khoản đã bị khóa -> trả mã 423 LOCKED + thông báo rõ ràng
            if (Boolean.FALSE.equals(user.getIsEnable())) {
                return ResponseEntity.status(423) // HttpStatus.LOCKED
                        .body(new ApiResponse(
                                "Tài khoản của bạn đã bị khóa (ví dụ: hủy lịch quá 3 lần). Vui lòng liên hệ Admin để mở khóa.",
                                Map.of(
                                        "banned", true,
                                        "userId", user.getId(),
                                        "email", user.getEmail(),
                                        "reason", "Vi phạm chính sách (ví dụ: hủy lịch nhiều lần)" // tuỳ bạn muốn hiển thị gì
                                )));
            }

            // 2️⃣ Kiểm tra password (mã hoá BCrypt)
            if (!passwordEncoder.matches(login.getPassword(), user.getPassword())) {
                return ResponseEntity.status(401)
                        .body(new ApiResponse("Invalid email or password", null));
            }

            // 3️⃣ Sinh JWT token
            String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUserType());

            // 4️⃣ Trả response
            return ResponseEntity.ok(new ApiResponse("Login success", Map.of(
                    "id", user.getId(),
                    "fullName", user.getFullName(),
                    "role", user.getUserType(),
                    "token", token
            )));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse("Server error: " + e.getMessage(), null));
        }
    }

    // 🔵 4️⃣ Đổi mật khẩu (cho patient đã đăng nhập)
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            String message = passwordService.changePassword(
                    request.getEmail(),
                    request.getOldPassword(),        // Mật khẩu hiện tại
                    request.getNewPassword(),       // Mật khẩu mới patient muốn đặt
                    request.getConfirmPassword()    // Xác nhận mật khẩu mới
            );
            return ResponseEntity.ok(new ApiResponse(message, null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse("Lỗi server: " + e.getMessage(), null));
        }
    }
}
