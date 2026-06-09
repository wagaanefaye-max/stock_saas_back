package com.stocksaas.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieFactory {

    @Value("${auth.cookie.name:auth_token}")
    private String authCookieName;

    @Value("${auth.cookie.max-age-seconds:86400}")
    private long authCookieMaxAgeSeconds;

    @Value("${auth.cookie.secure:false}")
    private boolean authCookieSecure;

    @Value("${auth.cookie.same-site:Lax}")
    private String authCookieSameSite;

    @Value("${auth.cookie.domain:}")
    private String authCookieDomain;

    public ResponseCookie createTokenCookie(String token) {
        return buildCookie(token, Duration.ofSeconds(authCookieMaxAgeSeconds));
    }

    public ResponseCookie createClearCookie() {
        return buildCookie("", Duration.ZERO);
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(authCookieName, value)
                .httpOnly(true)
                .secure(authCookieSecure)
                .path("/")
                .maxAge(maxAge)
                .sameSite(authCookieSameSite);
        if (authCookieDomain != null && !authCookieDomain.isBlank()) {
            builder.domain(authCookieDomain.trim());
        }
        return builder.build();
    }
}
