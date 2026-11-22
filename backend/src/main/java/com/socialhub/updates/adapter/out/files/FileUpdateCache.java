package com.socialhub.updates.adapter.out.files;

import com.socialhub.platform.AppPaths;
import com.socialhub.updates.domain.Release;
import com.socialhub.updates.domain.UpdateCache;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@code <datos>/actualizaciones.json}: cuándo se miró y qué release más nueva había. Solo datos públicos de GitHub. */
@Component
class FileUpdateCache implements UpdateCache {

    private final Path file;
    private final JsonMapper json = JsonMapper.builder().build();

    FileUpdateCache(AppPaths paths) { this.file = paths.dataDir().resolve("actualizaciones.json"); }

    @Override
    public Optional<Checked> load() {
        try {
            if (!Files.isRegularFile(file)) return Optional.empty();
            JsonNode root = json.readTree(Files.readString(file));
            Instant at = Instant.parse(root.path("checked_at").asString(""));
            JsonNode latest = root.path("latest");
            Release release = null;
            if (latest.isObject()) {
                List<Release.Asset> assets = new ArrayList<>();
                latest.path("assets").forEach(a -> assets.add(new Release.Asset(a.path("name").asString(""), a.path("url").asString(""))));
                release = new Release(latest.path("version").asString(""), latest.path("page_url").asString(""), List.copyOf(assets));
            }
            return Optional.of(new Checked(at, root.path("ok").asBoolean(false), release));
        } catch (IOException | JacksonException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    @Override
    public void save(Checked checked) {
        var root = json.createObjectNode();
        root.put("checked_at", checked.at().toString());
        root.put("ok", checked.ok());
        if (checked.latest() != null) {
            var latest = root.putObject("latest");
            latest.put("version", checked.latest().version());
            latest.put("page_url", checked.latest().pageUrl());
            var assets = latest.putArray("assets");
            checked.latest().assets().forEach(a -> assets.addObject().put("name", a.name()).put("url", a.url()));
        }
        try {
            Files.writeString(file, root.toString());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
