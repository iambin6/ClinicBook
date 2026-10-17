package com.nano.clinicbooking.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.stereotype.Component;

/**
 * Automatically trims and strips any leading/trailing quotes or spaces from OAuth2 Client credentials,
 * preventing 401 Unauthorized errors caused by copy-pasting issues into cloud environment variables (e.g. Render).
 */
@Slf4j
@Component
public class OAuth2PropertiesSanitizer implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof OAuth2ClientProperties props) {
            props.getRegistration().forEach((name, reg) -> {
                if (reg.getClientId() != null) {
                    String cleanedId = clean(reg.getClientId());
                    if (!cleanedId.equals(reg.getClientId())) {
                        log.info(">> [OAuth2 Sanitizer] Sanitized clientId for provider: {}", name);
                        reg.setClientId(cleanedId);
                    }
                }
                if (reg.getClientSecret() != null) {
                    String cleanedSecret = clean(reg.getClientSecret());
                    if (!cleanedSecret.equals(reg.getClientSecret())) {
                        log.info(">> [OAuth2 Sanitizer] Sanitized clientSecret for provider: {}", name);
                        reg.setClientSecret(cleanedSecret);
                    }
                }
            });
        }
        return bean;
    }

    private String clean(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        while ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() >= 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            } else {
                break;
            }
        }
        return trimmed;
    }
}
