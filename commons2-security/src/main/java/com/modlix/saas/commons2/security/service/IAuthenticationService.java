package com.modlix.saas.commons2.security.service;

import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;

public interface IAuthenticationService {

    String CACHE_NAME_TOKEN = "tokenCache";

    Authentication getAuthentication(
            boolean isBasic, String bearerToken, String clientCode, String appCode, HttpServletRequest request);
}
