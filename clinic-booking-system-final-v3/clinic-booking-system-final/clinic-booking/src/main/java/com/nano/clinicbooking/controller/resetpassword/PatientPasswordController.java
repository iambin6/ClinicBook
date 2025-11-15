package com.nano.clinicbooking.controller.resetpassword;

import com.nano.clinicbooking.dto.request.ResetPasswordRequest;
import com.nano.clinicbooking.dto.response.ApiResponse;
import com.nano.clinicbooking.service.reset_password.PatientPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin
public class PatientPasswordController {

    private final PatientPasswordService passwordService;

    /**
     * POST /auth/reset-password
     * Đổi mật khẩu từ mật khẩu tạm (từ email) sang mật khẩu mới do patient tự chọn
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            String message = passwordService.changePassword(
                    request.getEmail(),
                    request.getTemporaryPassword(), // Mật khẩu tạm từ email
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

    // Note: /auth/change-password endpoint đã được di chuyển sang AuthController
    // để tránh xung đột mapping
}
