package com.nano.clinicbooking.security;

import com.nano.clinicbooking.controller.event.EventController;
import com.nano.clinicbooking.controller.event.UserEventController;
import com.nano.clinicbooking.controller.health.HealthController;
import com.nano.clinicbooking.repository.search_user.UserRepository;
import com.nano.clinicbooking.service.ev_vc.EventService;
import com.nano.clinicbooking.service.login.CustomOidcUserService;
import com.nano.clinicbooking.service.login.FacebookOAuth2UserService;
import com.nano.clinicbooking.service.user.UserEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UserEventController.class, EventController.class, HealthController.class})
@Import({SecurityConfig.class, JwtFilter.class, EventAccessTest.OAuthConfiguration.class})
class EventAccessTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private EventService events;
    @MockitoBean private UserEventService registrations;
    @MockitoBean private CustomOidcUserService oidcUsers;
    @MockitoBean private FacebookOAuth2UserService facebookUsers;
    @MockitoBean private UserRepository users;
    @MockitoBean private JwtService jwt;
    @MockitoBean private ApplicationAvailability availability;

    @Test
    void publicHealthRequiresTheApplicationToBeReady() throws Exception {
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("STARTING"));
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthPermissionOnlyAllowsGetRequests() throws Exception {
        mvc.perform(post("/health")).andExpect(status().isUnauthorized());
    }

    @Test
    void guestCanReadPublicEvents() throws Exception {
        when(events.getAllEvents()).thenReturn(List.of());
        mvc.perform(get("/events")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/events/all")).andExpect(status().isOk());
        verifyNoInteractions(registrations);
    }

    @Test
    void guestCannotReadPersonalRegistrations() throws Exception {
        mvc.perform(get("/events/my")).andExpect(status().isUnauthorized());
        verifyNoInteractions(registrations);
    }

    @Test
    void doctorCannotReadPatientRegistrations() throws Exception {
        mvc.perform(get("/events/my").with(user("doctor").authorities(new SimpleGrantedAuthority("Doctor"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(registrations);
    }

    @Test
    void patientReadsOnlyTheirOwnRegistrations() throws Exception {
        when(registrations.getUserEvents(42L)).thenReturn(List.of());
        mvc.perform(get("/events/my").requestAttr("userId", 42L)
                        .with(user("patient").authorities(new SimpleGrantedAuthority("Patient"))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(registrations).getUserEvents(42L);
    }

    @Test
    void publicListingsDoNotAllowGuestRegistrationOrManagement() throws Exception {
        mvc.perform(post("/events/1/register")).andExpect(status().isUnauthorized());
        mvc.perform(post("/events/create")).andExpect(status().isUnauthorized());
        verifyNoInteractions(registrations, events);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OAuthConfiguration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("test")
                    .clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("https://example.invalid/authorize")
                    .tokenUri("https://example.invalid/token")
                    .userInfoUri("https://example.invalid/userinfo")
                    .userNameAttributeName("id").build());
        }
    }
}
