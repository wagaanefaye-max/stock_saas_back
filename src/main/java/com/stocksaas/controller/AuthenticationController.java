package com.stocksaas.controller;

import com.stocksaas.dto.AuthResponse;
import com.stocksaas.dto.ForgotPasswordRequest;
import com.stocksaas.dto.LoginRequest;
import com.stocksaas.dto.RegisterRequest;
import com.stocksaas.dto.RegisterResponse;
import com.stocksaas.dto.VerifyAccountRequest;
import com.stocksaas.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * Controller pour l'authentification (cookie HttpOnly pour le JWT).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "API pour l'authentification et l'inscription")
@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://sen-stocksaas.com",
                "https://sen-stocksaas.com",
                "http://164.132.43.247",
                "http://164.132.43.247:4200"
        },
        allowCredentials = "true"
)
public class AuthenticationController {

    @Value("${auth.cookie.name:auth_token}")
    private String authCookieName;

    @Value("${auth.cookie.max-age-seconds:86400}")
    private long authCookieMaxAgeSeconds;

    @Value("${auth.cookie.secure:false}")
    private boolean authCookieSecure;

    @Value("${auth.cookie.same-site:Lax}")
    private String authCookieSameSite;

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Connexion", description = "Authentifie un utilisateur et définit un cookie HttpOnly contenant le JWT")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            ResponseCookie cookie = ResponseCookie.from(authCookieName, response.getToken())
                    .httpOnly(true)
                    .secure(authCookieSecure)
                    .path("/")
                    .maxAge(Duration.ofSeconds(authCookieMaxAgeSeconds))
                    .sameSite(authCookieSameSite)
                    .build();
            response.setToken(null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(response);
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("message", "Email ou mot de passe incorrect"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la connexion"));
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Inscription", description = "Inscrit un nouvel utilisateur avec création d'entreprise. Un email de validation sera envoyé.")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de l'inscription"));
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Mot de passe oublié", description = "Envoie un email de réinitialisation de mot de passe si le compte existe")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.requestPasswordReset(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "Si un compte existe avec cet email, un lien de réinitialisation a été envoyé."));
        } catch (RuntimeException e) {
            // Pour des raisons de sécurité, renvoyer le même message générique côté client
            return ResponseEntity.ok(Map.of("message", "Si un compte existe avec cet email, un lien de réinitialisation a été envoyé."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la demande de réinitialisation"));
        }
    }

    @PostMapping("/verify-account")
    @Operation(summary = "Validation de compte", description = "Valide le compte, définit le mot de passe et définit le cookie d'authentification")
    public ResponseEntity<?> verifyAccount(@Valid @RequestBody VerifyAccountRequest request) {
        try {
            AuthResponse response = authService.verifyAccount(request);
            ResponseCookie cookie = ResponseCookie.from(authCookieName, response.getToken())
                    .httpOnly(true)
                    .secure(authCookieSecure)
                    .path("/")
                    .maxAge(Duration.ofSeconds(authCookieMaxAgeSeconds))
                    .sameSite(authCookieSameSite)
                    .build();
            response.setToken(null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la validation du compte"));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion", description = "Supprime le cookie d'authentification")
    public ResponseEntity<Map<String, String>> logout() {
        ResponseCookie clearCookie = ResponseCookie.from(authCookieName, "")
                .httpOnly(true)
                .secure(authCookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite(authCookieSameSite)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(Map.of("message", "Déconnexion réussie"));
    }
}
