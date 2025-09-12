package com.modlix.saas.commons2.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.security.filter.JWTTokenFilter;
import com.modlix.saas.commons2.security.service.IAuthenticationService;

import jakarta.servlet.http.HttpServletRequest;

public interface ISecurityConfiguration {
    default SecurityFilterChain springSecurityFilterChain(HttpSecurity http,
            IAuthenticationService authService, ObjectMapper om, String... exclusionList) throws Exception {

        return this.springSecurityFilterChain(http, authService, om, (HttpServletRequest) null, exclusionList);
    }

    default SecurityFilterChain springSecurityFilterChain(HttpSecurity http,
            IAuthenticationService authService, ObjectMapper om, HttpServletRequest matcher,
            String... exclusionList) throws Exception {

        CorsConfigurationSource source = corsConfigurationSource();

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(source))
                .authorizeHttpRequests(authorize -> {
                    authorize
                            .requestMatchers(HttpMethod.OPTIONS).permitAll()
                            .requestMatchers("(.*internal.*)").permitAll()
                            .requestMatchers("/actuator/**").permitAll();
                    if (exclusionList != null && exclusionList.length != 0)
                        authorize.requestMatchers(exclusionList).permitAll();

                    authorize.anyRequest().authenticated();
                })
                .headers(headers -> {
                    if (matcher == null)
                        return;
                    headers
                            .frameOptions(frameOptions -> frameOptions
                                    .sameOrigin())
                            .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'self'"));
                })
                .addFilterBefore(new JWTTokenFilter(authService, om),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    default CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
