package com.socialhub.pieces.application;

import com.socialhub.pieces.MissingMaterial;
import com.socialhub.pieces.domain.BrandMaterial;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.PublishingQueries;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Compara las publicaciones que van a producirse con lo que hay en las carpetas de material. Recorrer Drive es lento, así que
 * el resultado de cada promoción se guarda un minuto.
 */
@Service
@RequiredArgsConstructor
class MissingMaterialService implements MissingMaterial {

    static final Duration CACHE = Duration.ofSeconds(60);

    private record Cached(Instant at, BrandMaterial.Scan scan) {}

    private final WeeklySpecHandler specs;
    private final PromotionQueries promotions;
    private final BrandLooks looks;
    private final Clock clock;
    private final Map<String, Cached> scans = new ConcurrentHashMap<>();

    @Override
    public Report of(String promotionId) {
        Instant now = clock.instant();
        List<PublishingQueries.AwaitingContent> pending = specs.pending(promotionId, now);
        String style = promotions.visualStyleOf(promotionId);
        if (pending.isEmpty()) return new Report(true, 0, List.of(), null, style);
        String cover = looks.cover(promotionId).map(f -> f.getFileName().toString()).orElse(null);
        BrandMaterial.Scan scan = scan(promotionId, now);
        if (!scan.accessible()) return new Report(false, pending.size(), List.of(), cover, style);
        List<String> missing = new ArrayList<>();
        for (var p : pending) if (!scan.covers(p.title()) && !missing.contains(p.title())) missing.add(p.title());
        return new Report(true, pending.size(), List.copyOf(missing), cover, style);
    }

    private BrandMaterial.Scan scan(String promotionId, Instant now) {
        Cached cached = scans.get(promotionId);
        if (cached != null && cached.at().plus(CACHE).isAfter(now)) return cached.scan();
        List<Path> folders = new ArrayList<>();
        for (String folder : promotions.materialFolders(promotionId)) {
            try {
                folders.add(Path.of(folder));
            } catch (InvalidPathException e) {
                // Una ruta que no existe en este ordenador no cuenta
            }
        }
        BrandMaterial.Scan scan = BrandMaterial.scan(folders);
        scans.put(promotionId, new Cached(now, scan));
        return scan;
    }
}
