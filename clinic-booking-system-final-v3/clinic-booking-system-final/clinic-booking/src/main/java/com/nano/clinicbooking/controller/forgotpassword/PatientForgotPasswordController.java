package com.nano.clinicbooking.controller.forgotpassword;

import com.nano.clinicbooking.dto.request.ForgotPasswordRequest;
import com.nano.clinicbooking.dto.response.ApiResponse;
import com.nano.clinicbooking.service.forgotpassword.PatientForgotPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin
public class PatientForgotPasswordController {

    private final PatientForgotPasswordService forgotPasswordService;

    /**
     * POST /auth/forgot-password
     * Nhận email, tạo mật khẩu random mới, lưu vào DB và gửi email
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            String newPassword = forgotPasswordService.resetPasswordForPatient(request.getEmail());
            return ResponseEntity.ok(new ApiResponse(
                    "Mật khẩu mới đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư.",
                    null
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse("Lỗi server: " + e.getMessage(), null));
        }
    }
}
