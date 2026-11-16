package com.socialhub.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Deja en el registro los errores 5xx de la API: método, ruta y mensaje. Nunca el cuerpo de la petición ni de la
 * respuesta, ni la parte de la dirección después del «?» (podrían llevar datos personales o claves).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ApiErrorLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            Throwable cause = e.getCause() != null && e instanceof ServletException ? e.getCause() : e;
            log.error("Error en la API: {} {} -> {}: {}", request.getMethod(), request.getRequestURI(),
                    cause.getClass().getName(), cause.getMessage());
            throw e;
        }
        if (response.getStatus() >= 500 && com.socialhub.platform.AppModeHolder.haltedReason() == null) {
            log.error("La API respondió {} a {} {}", response.getStatus(), request.getMethod(), request.getRequestURI());
        }
    }
}
