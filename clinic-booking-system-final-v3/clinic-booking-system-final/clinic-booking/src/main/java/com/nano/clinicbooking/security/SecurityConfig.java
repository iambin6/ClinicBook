package com.nano.clinicbooking.security;

import com.nano.clinicbooking.service.login.CustomOidcUserService;
import com.nano.clinicbooking.service.login.FacebookOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@CrossOrigin
public class SecurityConfig {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final JwtFilter jwtFilter; // ✅ inject filter
    private final CustomOidcUserService customOidcUserService;
    private final FacebookOAuth2UserService FacebookOAuth2UserService;
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(req -> {
                    var c = new CorsConfiguration();
                    c.setAllowedOriginPatterns(List.of(
                            frontendUrl,
                            "http://localhost:5173",
                            "http://localhost:3000",
                            "https://*.onrender.com"
                    ));
                    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    c.setAllowedHeaders(List.of("*"));
                    c.setAllowCredentials(true);
                    return c;
                }))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + e.getMessage() + "\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"error\":\"forbidden\",\"message\":\"" + e.getMessage() + "\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        //  Cho phép public các API đăng nhập / đăng ký
                        .requestMatchers("/auth/**").permitAll()

                        //  Cho phép OAuth2 callback
                        .requestMatchers("/login/oauth2/code/**").permitAll()
                        .requestMatchers("/auth/oauth2/user-info").authenticated()
                        .requestMatchers("/auth/oauth2/logout").permitAll()

                        //  Cho phép tạo admin lần đầu (không cần token)
                        .requestMatchers("/auth/register-admin").permitAll()

                        // Cho phép GET danh sách chuyên khoa & bác sĩ (hiển thị công khai)
                        .requestMatchers(HttpMethod.GET, "/specialties/all").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/specialties/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/doctors/**").permitAll()

                        //  Cho phép chatbot  cần đăng nhập
                        .requestMatchers(HttpMethod.POST, "/chat/ask").authenticated()
                        // Cho phép tạo payment URL (cần đăng nhập)
                        .requestMatchers(HttpMethod.GET, "/payment/create").authenticated()
                        // Cho phép VNPay callback (public)
                        .requestMatchers(HttpMethod.GET, "/payment/vnpay-return").permitAll()
                        // Cho phép FE hiển thị ảnh công khai
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/images/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/images/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/images/**").permitAll()
                        .requestMatchers("/error/**").permitAll()
                        //  Các request khác yêu cầu JWT hợp lệ
                        //  Cho phép toàn bộ RSS dang nhao moi tao duoc
                        .requestMatchers("/rss-sources/**").authenticated()
                        .requestMatchers("/rss-sources/import/**").authenticated()
                        .requestMatchers("/rss-sources/feed/**").authenticated()
                        // FE đọc tin tức
                        .requestMatchers(HttpMethod.GET, "/rss-sources/news/**").authenticated()

                        // Cho phép tất cả roles xem events và vouchers
                        .requestMatchers(HttpMethod.GET, "/events/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events/my").authenticated()
                        .requestMatchers(HttpMethod.POST, "/events/{eventId}/register").authenticated()
                        .requestMatchers(HttpMethod.GET, "/vouchers/all").authenticated()
                        .requestMatchers(HttpMethod.GET, "/vouchers/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/vouchers/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/vouchers/user/{userId}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/vouchers/{id}/use").authenticated()

                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                                .userInfoEndpoint(u -> u
                                                // Google (OIDC) sẽ chạy vào customOidcUserService
                                                .oidcUserService(customOidcUserService)
                                                .userService(FacebookOAuth2UserService)
                                        // Nếu sau này có provider OAuth2 thuần (không OIDC), vẫn hỗ trợ:
//                                .userService(customOAuth2UserService)
                                )

                                .successHandler((req, res, auth) -> {
                                    System.out.println(">> OAuth2/OIDC SUCCESS for: " + auth.getName());
                                    // Redirect về frontend callback page
                                    res.sendRedirect(frontendUrl + "/oauth2/callback");
                                })
                                .failureHandler((req, res, ex) -> {
                                    ex.printStackTrace();
                                    System.err.println(">> OAuth2/OIDC FAILURE: " + ex.getMessage());
                                    res.setStatus(401);
                                    res.setContentType("text/plain;charset=UTF-8");
                                    res.getWriter().write("OAuth2 ERROR: " + ex.getMessage());
                                })
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/oauth2/logout")
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler((req, res, auth) -> {
                            res.setStatus(200);
                        })
                )


                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());




        // ✅ Thêm JwtFilter trước UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
