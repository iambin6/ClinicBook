package com.nano.clinicbooking.controller.login;

import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import com.nano.clinicbooking.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String googleClientSecret;

    @Value("${app.frontend.url:https://clinicbooking-j9r1.onrender.com}")
    private String frontendUrl;

    @GetMapping("/oauth2/status")
    public ResponseEntity<Map<String, Object>> getOAuth2Status() {
        Map<String, Object> status = new HashMap<>();
        boolean hasClientId = googleClientId != null && !googleClientId.isBlank();
        boolean hasSecret = googleClientSecret != null && !googleClientSecret.isBlank();
        
        status.put("googleClientIdConfigured", hasClientId);
        status.put("googleClientIdPrefix", hasClientId ? googleClientId.substring(0, Math.min(12, googleClientId.length())) + "..." : "NONE");
        status.put("googleClientSecretConfigured", hasSecret);
        status.put("googleClientSecretLength", hasSecret ? googleClientSecret.length() : 0);
        status.put("googleClientSecretPrefix", (hasSecret && googleClientSecret.length() >= 7) ? googleClientSecret.substring(0, 7) : "NONE");
        status.put("googleClientSecretSuffix", (hasSecret && googleClientSecret.length() >= 4) ? googleClientSecret.substring(googleClientSecret.length() - 4) : "NONE");
        status.put("hasQuotes", hasSecret && (googleClientSecret.startsWith("\"") || googleClientSecret.endsWith("\"") || googleClientSecret.startsWith("'") || googleClientSecret.endsWith("'")));
        status.put("hasWhitespace", hasSecret && (googleClientSecret.startsWith(" ") || googleClientSecret.endsWith(" ") || googleClientSecret.contains(" ")));
        status.put("frontendUrl", frontendUrl);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/oauth2/user-info")
    public ResponseEntity<Map<String, Object>> getOAuth2UserInfo(Authentication authentication) {
        try {
            if (authentication == null) {
                log.warn("OAuth2 user-info called without authentication");
                return ResponseEntity.status(401).body(Map.of(
                        "success", false,
                        "message", "User not authenticated"
                ));
            }
            log.info("Getting OAuth2 user info for: {}", authentication.getName());

            String email = null;
            if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
                email = oidcUser.getEmail();
                log.info("Processing OAuth2 (OIDC) user info for email: {}", email);
            } else if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
                Object em = oAuth2User.getAttributes().get("email");
                email = em == null ? null : String.valueOf(em);
                log.info("Processing OAuth2 (non-OIDC) user info for email: {}", email);
            }

            if (email != null && !email.isBlank()) {

                // Tìm user trong database
                Optional<User> userOpt = userRepository.findByEmail(email);

                if (userOpt.isPresent()) {
                    User user = userOpt.get();

                    // Tạo JWT token
                    String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUserType());

                    // Tạo response data
                    Map<String, Object> responseData = new HashMap<>();
                    responseData.put("success", true);
                    responseData.put("message", "Đăng nhập OAuth2 thành công");
                    responseData.put("token", token);
                    responseData.put("user", Map.of(
                            "id", user.getId(),
                            "email", user.getEmail(),
                            "fullName", user.getFullName(),
                            "userType", user.getUserType()
                    ));

                    log.info("OAuth2 user info retrieved successfully for user: {}", email);
                    return ResponseEntity.ok(responseData);
                } else {
                    log.error("User not found in database for email: {}", email);
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "User not found in database"
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error getting OAuth2 user info", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Internal server error: " + e.getMessage()
            ));
        }
        // If we reach here, we could not determine an email/principal
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Invalid authentication state"
        ));
    }
}
