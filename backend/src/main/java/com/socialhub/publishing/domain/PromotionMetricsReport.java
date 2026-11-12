package com.socialhub.publishing.domain;

import com.socialhub.publishing.domain.MetricsRepository.Metrics;
import com.socialhub.publishing.domain.MetricsRepository.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Métricas agregadas de una promoción: suma por red y comparación por formato y red con la media por publicación.
 * Solo cuentan las publicaciones con métrica leída (DONE). Lo que no se vio queda sin dato: nunca cuenta como cero.
 */
public record PromotionMetricsReport(int measured, int pending, List<NetworkTotals> byNetwork, List<FormatAverages> byFormat) {

    /** Por debajo de este número de publicaciones, un grupo se marca como «pocos datos». */
    public static final int LOW_SAMPLE_BELOW = 5;

    /** {@code platform} es null en la fila TOTAL. */
    public record NetworkTotals(@Nullable Platform platform, int publications, Sums sums) {}

    public record FormatAverages(String type, Platform platform, int publications, boolean lowSample, Averages averages) {}

    public record Sums(@Nullable Long views, @Nullable Long reach, @Nullable Long likes, @Nullable Long comments,
                       @Nullable Long saves, @Nullable Long shares) {}

    public record Averages(@Nullable Double views, @Nullable Double reach, @Nullable Double likes, @Nullable Double comments,
                           @Nullable Double saves, @Nullable Double shares) {}

    /** @param publications las publicaciones de la promoción; @param metrics sus métricas por id de publicación */
    public static PromotionMetricsReport of(List<Publication> publications, Map<String, Metrics> metrics) {
        List<Row> rows = new ArrayList<>();
        int pending = 0;
        for (Publication p : publications) {
            Metrics m = metrics.get(p.getId());
            if (m != null && m.status() == Status.DONE) rows.add(new Row(p, m));
            else if (p.getStatus() == PublicationStatus.PUBLISHED) pending++;
        }
        List<NetworkTotals> networks = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            networks.add(totals(platform, rows.stream().filter(r -> r.p.getPlatform() == platform).map(r -> r.m).toList()));
        }
        networks.add(totals(null, rows.stream().map(r -> r.m).toList()));

        Map<String, Map<Platform, List<Metrics>>> groups = new TreeMap<>();
        for (Row r : rows) {
            groups.computeIfAbsent(r.p.getType(), k -> new EnumMap<>(Platform.class))
                    .computeIfAbsent(r.p.getPlatform(), k -> new ArrayList<>()).add(r.m);
        }
        List<FormatAverages> formats = new ArrayList<>();
        groups.forEach((type, byPlatform) -> byPlatform.forEach((platform, list) ->
                formats.add(new FormatAverages(type, platform, list.size(), list.size() < LOW_SAMPLE_BELOW, averages(list)))));
        formats.sort(Comparator.comparing(FormatAverages::type).thenComparing(FormatAverages::platform));
        return new PromotionMetricsReport(rows.size(), pending, List.copyOf(networks), List.copyOf(formats));
    }

    private record Row(Publication p, Metrics m) {}

    private static NetworkTotals totals(@Nullable Platform platform, List<Metrics> list) {
        return new NetworkTotals(platform, list.size(), new Sums(sum(list, Metrics::views), sum(list, Metrics::reach),
                sum(list, Metrics::likes), sum(list, Metrics::comments), sum(list, Metrics::saves), sum(list, Metrics::shares)));
    }

    private static Averages averages(List<Metrics> list) {
        return new Averages(mean(list, Metrics::views), mean(list, Metrics::reach), mean(list, Metrics::likes),
                mean(list, Metrics::comments), mean(list, Metrics::saves), mean(list, Metrics::shares));
    }

    /** Suma de las publicaciones que tienen la cifra; null si ninguna la tiene. */
    private static @Nullable Long sum(List<Metrics> list, Function<Metrics, @Nullable Long> figure) {
        long total = 0;
        boolean any = false;
        for (Metrics m : list) {
            Long v = figure.apply(m);
            if (v != null) { total += v; any = true; }
        }
        return any ? total : null;
    }

    /** Media entre las publicaciones que tienen la cifra; null si ninguna la tiene. */
    private static @Nullable Double mean(List<Metrics> list, Function<Metrics, @Nullable Long> figure) {
        long total = 0;
        int n = 0;
        for (Metrics m : list) {
            Long v = figure.apply(m);
            if (v != null) { total += v; n++; }
        }
        return n == 0 ? null : (double) total / n;
    }
}
