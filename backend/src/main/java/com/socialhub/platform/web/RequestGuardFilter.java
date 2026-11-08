package com.socialhub.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Seguridad sencilla para una aplicación que solo escucha en 127.0.0.1:
 * <ul>
 *   <li>Solo se aceptan peticiones dirigidas a {@code localhost}, {@code 127.0.0.1} o {@code [::1]}
 *       (protege frente a «DNS rebinding»: una web externa que se hace pasar por localhost).</li>
 *   <li>Las peticiones que cambian algo necesitan la cabecera {@code X-SocialHub-Request: 1}.
 *       Una web externa abierta en el navegador no puede añadirla sin permiso CORS, que no se da.</li>
 * </ul>
 */
@Component
class RequestGuardFilter extends OncePerRequestFilter {

    static final String HEADER = "X-SocialHub-Request";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!LOCAL_HOSTS.contains(host(request))) {
            reject(response, "Solo se aceptan peticiones a localhost");
            return;
        }
        if (request.getRequestURI().startsWith("/api/")
                && !SAFE_METHODS.contains(request.getMethod())
                && !"1".equals(request.getHeader(HEADER))) {
            reject(response, "Falta la cabecera " + HEADER + ": 1");
            return;
        }
        String halted = com.socialhub.platform.AppModeHolder.haltedReason();
        if (halted != null && request.getRequestURI().startsWith("/api/") && !SAFE_METHODS.contains(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"title\":\"SocialHub está parado\",\"status\":503,\"detail\":\""
                    + halted.replace("\"", "'") + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Nombre del servidor al que va dirigida la petición (de la cabecera Host), sin el puerto. */
    static String host(HttpServletRequest request) {
        String name = request.getServerName();
        return name == null ? "" : name.toLowerCase();
    }

    private static void reject(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"" + detail + "\"}");
    }
}
