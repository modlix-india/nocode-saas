package com.modlix.saas.commons2.security.filter;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.IAuthenticationService;
import com.modlix.saas.commons2.security.util.ServerHttpRequestUtil;
import com.modlix.saas.commons2.security.util.LogUtil;

import lombok.RequiredArgsConstructor;
import com.modlix.saas.commons2.util.Tuples;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

@RequiredArgsConstructor
public class JWTTokenFilter implements Filter {

    private final IAuthenticationService authService;
    private final ObjectMapper mapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        Tuples.Tuple2<Boolean, String> tuple = ServerHttpRequestUtil.extractBasicNBearerToken(httpRequest);

        boolean isBasic = tuple.getT1();
        String bearerToken = tuple.getT2();

        String clientCode = httpRequest.getHeader("clientCode");
        String appCode = httpRequest.getHeader("appCode");
        final String cc = clientCode == null || clientCode.isEmpty() ? null : clientCode;
        final String ac = appCode == null || appCode.isEmpty() ? null : appCode;

        final String debugCode = httpRequest.getHeader(LogUtil.DEBUG_KEY);

        Authentication auth = this.authService.getAuthentication(isBasic, bearerToken, cc, ac, httpRequest);

        if (auth != null) {
            ContextAuthentication newCA = mapper.convertValue(auth, ContextAuthentication.class)
                    .setUrlAppCode(ac)
                    .setUrlClientCode(cc);

            SecurityContextHolder.getContext().setAuthentication(newCA);
        }

        chain.doFilter(request, response);
    }
}
