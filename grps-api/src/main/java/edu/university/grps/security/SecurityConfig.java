package edu.university.grps.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.grps.common.ErrorCode;
import edu.university.grps.common.GrpsProperties;
import edu.university.grps.common.RequestIdFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Signed file links: no session, and the web app (only) may embed them for PDF and image previews. */
    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain fileLinkChain(HttpSecurity http, GrpsProperties props) throws Exception {
        String ancestors = String.join(" ", props.webOrigin().split(","));
        http.securityMatcher("/api/v1/files/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.disable())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'self' " + ancestors)))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper mapper, Clock clock) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .frameOptions(f -> f.deny()))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/password/**",
                                "/api/v1/auth/activate").permitAll()
                        .requestMatchers("/api/v1/files/**", "/api/v1/public/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(mapper, req, res, ErrorCode.UNAUTHENTICATED,
                                "Your session has expired or is missing. Sign in again.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(mapper, req, res, ErrorCode.UNAUTHENTICATED,
                                "Sign in to continue."))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(mapper, req, res, ErrorCode.FORBIDDEN,
                                "Your role does not allow this action.")))
                .addFilterAfter(new RateLimitFilter(mapper, clock), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Maps the token's roles claim to authorities. Super Admin holds every Admin permission. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    if (role.equals("SUPER_ADMIN")) authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GrpsProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(props.webOrigin().split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", RequestIdFilter.HEADER));
        config.setExposedHeaders(List.of(RequestIdFilter.HEADER, "Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    static void writeProblem(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res, ErrorCode code,
                             String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://grps/errors/" + code.typeSlug());
        body.put("title", code.title());
        body.put("status", code.status().value());
        body.put("code", code.name());
        body.put("detail", detail);
        body.put("instance", req.getRequestURI());
        body.put("traceId", MDC.get(RequestIdFilter.MDC_KEY));
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), body);
    }
}
