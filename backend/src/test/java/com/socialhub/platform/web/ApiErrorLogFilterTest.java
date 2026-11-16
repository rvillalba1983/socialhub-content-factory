package com.socialhub.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Los 5xx de la API quedan en el registro con método, ruta y mensaje; nunca con la consulta ni el cuerpo. */
class ApiErrorLogFilterTest {

    private final ApiErrorLogFilter filter = new ApiErrorLogFilter();
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(ApiErrorLogFilter.class);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(events);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
        r.setQueryString("access_token=SECRETO");
        r.setContent("{\"email\":\"persona@example.com\"}".getBytes());
        return r;
    }

    @Test
    void registraUnaExcepcionConRutaYMensajeYLaRelanza() {
        assertThatThrownBy(() -> filter.doFilter(request("/api/publications/p-1/files"), new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException(new IllegalStateException("Drive no responde")); }))
                .isInstanceOf(ServletException.class);
        assertThat(events.list).hasSize(1);
        ILoggingEvent e = events.list.getFirst();
        assertThat(e.getLevel()).isEqualTo(Level.ERROR);
        assertThat(e.getFormattedMessage()).contains("POST /api/publications/p-1/files")
                .contains("IllegalStateException").contains("Drive no responde")
                .doesNotContain("SECRETO").doesNotContain("persona@example.com");
    }

    @Test
    void registraUnaRespuestaCincoCientos() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/api/tasks"), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(500));
        assertThat(events.list).hasSize(1);
        assertThat(events.list.getFirst().getFormattedMessage()).contains("500").contains("POST /api/tasks")
                .doesNotContain("SECRETO").doesNotContain("persona@example.com");
    }

    @Test
    void norRegistraLoQueNoEsError() throws Exception {
        filter.doFilter(request("/api/tasks"), new MockHttpServletResponse(), (req, res) -> ((MockHttpServletResponse) res).setStatus(409));
        filter.doFilter(request("/index.html"), new MockHttpServletResponse(), (req, res) -> ((MockHttpServletResponse) res).setStatus(500));
        assertThat(events.list).isEmpty();
    }
}
