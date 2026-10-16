package com.nano.clinicbooking.service.login;

import com.nano.clinicbooking.model.Patient;
import com.nano.clinicbooking.model.User;
import com.nano.clinicbooking.repository.patient.PatientRepository;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import com.nano.clinicbooking.service.email.send_register.AiEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final AiEmailService aiEmailService;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest req) {
        log.info(">> Google OIDC loadUser started for client: {}", req.getClientRegistration().getRegistrationId());
        OidcUser oidcUser = super.loadUser(req);

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            Object emailAttr = oidcUser.getAttributes().get("email");
            email = emailAttr == null ? null : String.valueOf(emailAttr);
        }
        if (email == null || email.isBlank()) {
            log.error("Google account has no email");
            throw new IllegalStateException("Google account has no email");
        }

        String fullName = oidcUser.getFullName();
        if (fullName == null || fullName.isBlank()) {
            String given = oidcUser.getGivenName() == null ? "" : oidcUser.getGivenName();
            String family = oidcUser.getFamilyName() == null ? "" : oidcUser.getFamilyName();
            fullName = (given + " " + family).trim();
            if (fullName.isBlank()) fullName = email;
        }

        boolean isFirstLogin = false;
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Lần đầu -> tạo Patient mới
            log.info("Google user not found. Creating new Patient for: {}", email);
            Patient p = new Patient();
            p.setEmail(email);
            p.setPassword("{noop}GOOGLE_LOGIN");
            p.setUserType("Patient");
            p.setIsEnable(true);
            p.setFullName(fullName);
            user = patientRepository.save(p);
            isFirstLogin = true;
        } else {
            // Đã tồn tại -> cập nhật thông tin cơ bản, giữ nguyên vai trò (Admin, Doctor, etc.)
            log.info("Google user already exists: {} with role: {}", email, user.getUserType());
            user.setFullName(fullName);
            user.setIsEnable(true);
            userRepository.save(user);
        }

        if (isFirstLogin) {
            try {
                aiEmailService.sendRegisterSuccessEmail(user);
                log.info("Welcome email triggered for: {}", email);
            } catch (Exception ex) {
                log.warn("Cannot send welcome email for Google user {}: {}", email, ex.getMessage());
            }
        }

        return oidcUser.getUserInfo() != null
                ? new DefaultOidcUser(
                        Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                        oidcUser.getIdToken(),
                        oidcUser.getUserInfo(),
                        "sub"
                )
                : new DefaultOidcUser(
                        Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                        oidcUser.getIdToken(),
                        "sub"
                );
    }
}

