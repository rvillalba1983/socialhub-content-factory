package com.socialhub.backup.adapter.out.postgres;

import com.socialhub.backup.domain.DatabaseArchive;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.DomainException;
import com.socialhub.platform.postgres.ModeDataSources;
import java.io.BufferedReader;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * El PostgreSQL incluido no trae {@code pg_dump}, así que cada tabla se exporta con {@code COPY ... TO STDOUT} del
 * driver JDBC, todas en una transacción de solo lectura (una foto coherente sin parar nada).
 *
 * <p>Al restaurar, los datos se cargan en un esquema temporal creado con las migraciones hasta la versión de la
 * copia; después se migra a la última versión y se cambia por el esquema {@code public} en una sola transacción.
 */
@Slf4j
@Component
class PostgresArchive implements DatabaseArchive {

    static final String MANIFEST = "manifest.json";
    static final String TABLES = "tablas/";
    static final String TEMP_SCHEMA = "restore_tmp";
    static final int FORMAT = 1;
    private static final String HISTORY = "flyway_schema_history";
    private static final Pattern IDENT = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Manifest(int format, String app, Instant createdAt, String migrationVersion,
                    List<TableInfo> tables, Map<String, @Nullable Long> sequences) {}

    record TableInfo(String name, long rows) {}

    private final DataSource real;
    private final String url;
    private final String user;
    private final Clock clock;

    PostgresArchive(ModeDataSources databases, Clock clock) {
        this.clock = clock;
        this.real = databases.of(AppMode.REAL);
        this.url = databases.jdbcUrl(AppMode.REAL);
        this.user = databases.user();
    }

    // ------------------------------------------------------------------ Exportar

    @Override
    public void export(OutputStream out) throws IOException {
        try (Connection c = real.getConnection()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setReadOnly(true);
            try {
                String version = migrationVersion(c, "public");
                if (version == null) throw new IOException("La base de datos no tiene migraciones aplicadas");
                List<String> tables = tables(c, "public");
                Map<String, @Nullable Long> sequences = sequences(c, "public");
                CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
                List<TableInfo> infos = new ArrayList<>();
                for (String table : tables) {
                    zip.putNextEntry(new ZipEntry(TABLES + table + ".csv"));
                    long rows = copy.copyOut("COPY public." + quote(table) + " TO STDOUT WITH (FORMAT csv, HEADER)", keepOpen(zip));
                    zip.closeEntry();
                    infos.add(new TableInfo(table, rows));
                }
                zip.putNextEntry(new ZipEntry(MANIFEST));
                zip.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                        new Manifest(FORMAT, "SocialHub", clock.instant(), version, infos, sequences)));
                zip.closeEntry();
                zip.finish();
            } finally {
                c.rollback();
                c.setReadOnly(false);
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IOException("No se pudo exportar la base de datos: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ Restaurar

    @Override
    public void restore(Path zipPath) {
        DataSource own = new DriverManagerDataSource(url, user, "");
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Manifest manifest = manifest(zip);
            check(manifest, zip);
            try (Connection c = own.getConnection(); Statement st = c.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS " + TEMP_SCHEMA + " CASCADE");
            }
            // 1. Tablas como estaban en la versión de la copia
            flyway(own).target(MigrationVersion.fromVersion(manifest.migrationVersion())).load().migrate();
            // 2. Datos
            try (Connection c = own.getConnection()) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    // Sin comprobar claves ajenas mientras se carga: el orden de las tablas no importa
                    st.execute("SET LOCAL session_replication_role = replica");
                }
                List<String> existing = tables(c, TEMP_SCHEMA);
                // Las migraciones crean algunos datos iniciales; en la copia ya están los buenos
                if (!existing.isEmpty()) {
                    try (Statement st = c.createStatement()) {
                        st.execute("TRUNCATE " + existing.stream().map(t -> TEMP_SCHEMA + "." + quote(t)).collect(Collectors.joining(", ")));
                    }
                }
                CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
                for (TableInfo t : manifest.tables()) {
                    if (!existing.contains(t.name())) throw new DomainException("La copia tiene una tabla desconocida: " + t.name());
                    ZipEntry entry = zip.getEntry(TABLES + t.name() + ".csv");
                    String columns = columns(zip, entry);
                    try (InputStream in = zip.getInputStream(entry)) {
                        copy.copyIn("COPY " + TEMP_SCHEMA + "." + quote(t.name()) + " (" + columns + ") FROM STDIN WITH (FORMAT csv, HEADER)", in);
                    }
                }
                try (Statement st = c.createStatement()) {
                    for (Map.Entry<String, @Nullable Long> s : manifest.sequences().entrySet()) {
                        if (s.getValue() == null || s.getValue() < 1 || !IDENT.matcher(s.getKey()).matches()) continue;
                        st.execute("SELECT setval('" + TEMP_SCHEMA + "." + s.getKey() + "', " + s.getValue() + ", true) "
                                + "WHERE EXISTS (SELECT 1 FROM pg_sequences WHERE schemaname = '" + TEMP_SCHEMA
                                + "' AND sequencename = '" + s.getKey() + "')");
                    }
                }
                c.commit();
            }
            // 3. A la última versión
            flyway(own).load().migrate();
            // 4. Cambio de esquema de una vez
            try (Connection c = own.getConnection()) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    st.execute("DROP SCHEMA public CASCADE");
                    st.execute("ALTER SCHEMA " + TEMP_SCHEMA + " RENAME TO public");
                }
                c.commit();
            }
            log.info("Copia restaurada: {} ({} tablas, versión {})", zipPath.getFileName(), manifest.tables().size(), manifest.migrationVersion());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la copia: " + e.getMessage(), e);
        } catch (SQLException e) {
            dropTemp(own);
            throw new IllegalStateException("No se pudo restaurar la copia: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            dropTemp(own);
            throw e;
        }
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway(DataSource ds) {
        return Flyway.configure().dataSource(ds).schemas(TEMP_SCHEMA).createSchemas(true).locations("classpath:db/migration");
    }

    private void check(Manifest m, ZipFile zip) {
        if (m.format() != FORMAT) throw new DomainException("Formato de copia desconocido: " + m.format());
        var current = Flyway.configure().dataSource(real).locations("classpath:db/migration").load().info().current();
        if (current == null || MigrationVersion.fromVersion(m.migrationVersion()).isNewerThan(current.getVersion().getVersion())) {
            throw new DomainException("La copia es de una versión más nueva de SocialHub (" + m.migrationVersion()
                    + "). Actualiza SocialHub antes de restaurarla.");
        }
        for (TableInfo t : m.tables()) {
            if (!IDENT.matcher(t.name()).matches() || zip.getEntry(TABLES + t.name() + ".csv") == null) {
                throw new DomainException("La copia está incompleta: falta la tabla " + t.name());
            }
        }
    }

    private static Manifest manifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry(MANIFEST);
        if (entry == null) throw new DomainException("No es una copia de SocialHub: falta " + MANIFEST);
        try (InputStream in = zip.getInputStream(entry)) {
            return JSON.readValue(in, Manifest.class);
        }
    }

    /** Las columnas de la cabecera del CSV, entre comillas (una tabla puede haber cambiado de columnas después). */
    private static String columns(ZipFile zip, ZipEntry entry) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            String header = r.readLine();
            if (header == null || header.isBlank()) throw new DomainException("Tabla sin cabecera en la copia: " + entry.getName());
            List<String> names = Arrays.stream(header.split(",")).map(h -> h.replace("\"", "").trim()).toList();
            for (String n : names) if (!IDENT.matcher(n).matches()) throw new DomainException("Columna no válida en la copia: " + n);
            return names.stream().map(PostgresArchive::quote).collect(Collectors.joining(", "));
        }
    }

    private static void dropTemp(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + TEMP_SCHEMA + " CASCADE");
        } catch (SQLException e) {
            log.warn("No se pudo borrar el esquema temporal: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Utilidades

    private static @Nullable String migrationVersion(Connection c, String schema) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM " + schema + "." + HISTORY
                     + " WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static List<String> tables(Connection c, String schema) throws SQLException {
        List<String> out = new ArrayList<>();
        try (var ps = c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname = ? AND tablename <> ? ORDER BY tablename")) {
            ps.setString(1, schema);
            ps.setString(2, HISTORY);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getString(1)); }
        }
        return out;
    }

    private static Map<String, @Nullable Long> sequences(Connection c, String schema) throws SQLException {
        Map<String, @Nullable Long> out = new LinkedHashMap<>();
        try (var ps = c.prepareStatement("SELECT sequencename, last_value FROM pg_sequences WHERE schemaname = ? ORDER BY sequencename")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long v = rs.getLong(2);
                    out.put(rs.getString(1), rs.wasNull() ? null : v);
                }
            }
        }
        return out;
    }

    private static String quote(String identifier) {
        if (!IDENT.matcher(identifier).matches()) throw new IllegalArgumentException("Nombre no válido: " + identifier);
        return "\"" + identifier + "\"";
    }

    /** COPY cierra el flujo al terminar; el zip tiene que seguir abierto para la tabla siguiente. */
    private static OutputStream keepOpen(OutputStream out) {
        return new FilterOutputStream(out) {
            @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
            @Override public void close() throws IOException { flush(); }
        };
    }
}
