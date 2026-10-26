package com.socialhub.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protege la API:
 * <ul>
 *   <li>Sin sesión válida, 401 (salvo las rutas de acceso).</li>
 *   <li>Las peticiones que cambian algo deben llevar la cabecera {@value #CSRF_HEADER}. Otras webs que abras
 *       en el navegador no pueden añadirla, así que no pueden actuar en tu nombre.</li>
 * </ul>
 */
@Component
class AuthFilter extends OncePerRequestFilter {

    static final String COOKIE = "SOCIALHUB_SESSION";
    static final String CSRF_HEADER = "X-SocialHub-Request";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AuthService auth;

    AuthFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && !"1".equals(request.getHeader(CSRF_HEADER))) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "Falta la cabecera " + CSRF_HEADER + ".");
            return;
        }
        if (request.getRequestURI().startsWith("/api/v1/auth/") || auth.isValid(token(request))) {
            chain.doFilter(request, response);
            return;
        }
        reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Inicia sesión para continuar.");
    }

    static String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static void reject(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
