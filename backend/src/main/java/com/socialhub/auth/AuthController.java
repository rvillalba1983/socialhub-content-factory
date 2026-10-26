package com.socialhub.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    record Credentials(String password) {}

    @GetMapping("/status")
    Map<String, Object> status(HttpServletRequest request) {
        return Map.of("needsSetup", auth.needsSetup(),
                "authenticated", auth.isValid(AuthFilter.token(request)),
                "minLength", AuthService.MIN_PASSWORD_LENGTH);
    }

    @PostMapping("/setup")
    ResponseEntity<Void> setup(@RequestBody Credentials credentials) {
        return withSession(auth.setup(credentials.password()));
    }

    @PostMapping("/login")
    ResponseEntity<Map<String, String>> login(@RequestBody Credentials credentials) {
        return auth.login(credentials.password())
                .map(token -> ResponseEntity.noContent()
                        .header(HttpHeaders.SET_COOKIE, cookie(token, AuthService.SESSION_LENGTH.toSeconds()).toString())
                        .<Map<String, String>>build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("detail", "Contraseña incorrecta.")));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.logout(AuthFilter.token(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
    }

    private static ResponseEntity<Void> withSession(String token) {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie(token, AuthService.SESSION_LENGTH.toSeconds()).toString())
                .build();
    }

    private static ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(AuthFilter.COOKIE, value)
                .httpOnly(true).sameSite("Strict").path("/").maxAge(maxAgeSeconds).build();
    }
}
