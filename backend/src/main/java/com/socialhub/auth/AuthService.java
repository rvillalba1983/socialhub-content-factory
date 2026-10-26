package com.socialhub.auth;

import com.socialhub.audit.AuditLog;
import com.socialhub.platform.AppPaths;
import com.socialhub.platform.DomainException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contraseña local de SocialHub. Se crea en el primer arranque, se guarda como hash (BCrypt) y abre
 * sesiones de 30 días. Si existe el archivo {@code reset-password} en la carpeta de datos, al arrancar
 * se borra la contraseña para crear una nueva.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final Duration SESSION_LENGTH = Duration.ofDays(30);
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final String RESET_FILE = "reset-password";

    private final JdbcClient jdbc;
    private final AuditLog audit;
    private final AppPaths paths;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcClient jdbc, AuditLog audit, AppPaths paths, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.paths = paths;
        this.clock = clock;
    }

    public boolean needsSetup() {
        return jdbc.sql("select count(*) from app_credential").query(Integer.class).single() == 0;
    }

    /** Crea la contraseña la primera vez y abre sesión. */
    @Transactional
    public String setup(String password) {
        if (!needsSetup()) throw new DomainException("La contraseña ya está creada.");
        validate(password);
        jdbc.sql("insert into app_credential (id, password_hash) values (1, ?)").param(encoder.encode(password)).update();
        audit.record("Raúl", "auth.setup", "app", null, null);
        return openSession();
    }

    @Transactional
    public Optional<String> login(String password) {
        Optional<String> hash = jdbc.sql("select password_hash from app_credential where id = 1").query(String.class).optional();
        if (hash.isEmpty() || password == null || !encoder.matches(password, hash.get())) {
            audit.record("desconocido", "auth.login_failed", "app", null, null);
            return Optional.empty();
        }
        audit.record("Raúl", "auth.login", "app", null, null);
        return Optional.of(openSession());
    }

    @Transactional
    public void logout(String token) {
        if (token != null) jdbc.sql("delete from app_session where token_hash = ?").param(hash(token)).update();
    }

    /** Comprueba el token de la cookie y alarga la marca de último uso. */
    @Transactional
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        return jdbc.sql("update app_session set last_seen_at = now() where token_hash = ? and expires_at > ?")
                .params(hash(token), Timestamp.from(clock.instant())).update() == 1;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resetIfRequested() {
        Path flag = paths.dataDir().resolve(RESET_FILE);
        jdbc.sql("delete from app_session where expires_at < ?").param(Timestamp.from(clock.instant())).update();
        if (!Files.exists(flag)) return;
        jdbc.sql("delete from app_session").update();
        jdbc.sql("delete from app_credential").update();
        audit.record("Raúl", "auth.reset", "app", null, "Archivo " + RESET_FILE);
        try {
            Files.delete(flag);
        } catch (IOException e) {
            log.warn("No se pudo borrar {}: {}", flag, e.getMessage());
        }
        log.info("Contraseña reiniciada: al abrir SocialHub se pedirá crear una nueva");
    }

    private String openSession() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.sql("insert into app_session (token_hash, expires_at) values (?, ?)")
                .params(hash(token), Timestamp.from(now.plus(SESSION_LENGTH))).update();
        return token;
    }

    private static void validate(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
