package com.socialhub.publishing.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Cómo ha acabado (o va) una publicación, con solo datos reales: el estado de la publicación, su trabajo de publicación y
 * el calendario que ha leído Claude. Sin confirmación no se da por publicada: queda «Sin confirmar».
 *
 * @param at hora real: la que dice el calendario de la vía o, si no, cuando SocialHub confirmó la publicación (solo
 *           {@link Result#PUBLISHED} y {@link Result#DELAYED} cuando ya salió)
 * @param atSource de dónde sale {@code at}
 * @param route vía usada, tal como se muestra («API oficial», «Publer», «Publora» o «Manual»)
 * @param url enlace al post, cuando existe
 * @param reason por qué falló, se retrasó o no se ha podido confirmar
 * @param advice qué hacer
 */
public record PublicationOutcome(Result result, @Nullable Instant at, @Nullable AtSource atSource, @Nullable String route,
                                 @Nullable String url, @Nullable String reason, @Nullable String advice) {

    public enum Result {
        PUBLISHED("Publicada"), FAILED("Falló"), DELAYED("Retrasada"), PENDING("Pendiente"), UNCONFIRMED("Sin confirmar");

        private final String label;

        Result(String label) { this.label = label; }

        public String label() { return label; }
    }

    public enum AtSource { CALENDAR, CONFIRMATION }

    /** Comienzo de la incidencia que anota el motor cuando publica con retraso (SocialHub estaba cerrado a su hora). */
    public static final String DELAY_PREFIX = "Publicada con ";
    /** Retraso a partir del cual se avisa (la ronda del motor es cada minuto y una subida de vídeo tarda). */
    public static final Duration DELAY_NOTICE = Duration.ofMinutes(10);
    /** Pasada su hora, lo que se espera antes de decir que no ha salido (la comprobación llega 15 minutos después). */
    public static final Duration GRACE = Duration.ofMinutes(15);
    public static final String DOUBT_PREFIX = "Revisar antes de reintentar: ";

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_HOUR = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /** Hay algo que Raúl debe mirar: ha fallado o se ha retrasado. */
    public boolean needsAttention() { return result == Result.FAILED || result == Result.DELAYED; }

    /**
     * El resultado de una publicación, o vacío si está cancelada (no tiene resultado que enseñar).
     *
     * @param job su trabajo de publicación, si alguna vez se programó
     * @param external lo que dice el calendario leído de fuera (Publer, Publora, la red) de esa publicación
     */
    public static Optional<PublicationOutcome> of(Publication p, @Nullable PublishJob job,
                                                  List<ExternalCalendarRepository.Item> external, Instant now) {
        if (p.getStatus() == PublicationStatus.CANCELLED) return Optional.empty();
        ZoneId zone = ZoneId.of(p.getTimezone());
        var published = external.stream().filter(i -> i.status() == ExternalCalendarRepository.Status.PUBLISHED)
                .max(java.util.Comparator.comparing(ExternalCalendarRepository.Item::at)).orElse(null);
        var failedOutside = external.stream().filter(i -> i.status() == ExternalCalendarRepository.Status.FAILED).findFirst().orElse(null);
        String route = routeOf(job, published);
        String flag = p.getFlag();

        if (p.getStatus() == PublicationStatus.PUBLISHED) return Optional.of(published(p, job, published, route, zone));

        boolean error = p.getStatus() == PublicationStatus.PUBLISH_ERROR
                || (job != null && job.getStatus() == PublishJob.Status.FAILED);
        if (error || failedOutside != null) {
            String reason = job != null && job.getLastError() != null ? job.getLastError()
                    : flag != null ? flag
                    : failedOutside != null ? failedOutside.source().label() + " la marca como fallida"
                    : "Error de publicación";
            String advice;
            if (flag != null && flag.startsWith(DOUBT_PREFIX)) {
                advice = "Mira en la red si ya está publicada antes de volver a programarla";
            } else if (job != null && job.getManualFile() != null) {
                advice = "Publícala tú a mano (hay un LEEME con los pasos) o cámbiale la hora y vuelve a programarla";
            } else {
                advice = "Corrige el motivo y vuelve a programarla, o publícala tú a mano";
            }
            return Optional.of(new PublicationOutcome(Result.FAILED, null, null, route, null, reason, advice));
        }

        Instant at = p.scheduledAt();
        if (now.isBefore(at.plus(GRACE))) {
            return Optional.of(new PublicationOutcome(Result.PENDING, null, null, route, null, null, null));
        }
        // Pasó su hora y no hay confirmación
        String when = DAY_HOUR.format(at.atZone(zone));
        if (job != null && job.getStatus() == PublishJob.Status.CHECKING) {
            return Optional.of(new PublicationOutcome(Result.UNCONFIRMED, null, null, route, null,
                    "Pasó su hora (" + when + ") y aún no se ha podido confirmar" + (route == null ? "" : " en " + route),
                    "SocialHub la sigue comprobando. Si quieres, mira tú en la red si ya salió"));
        }
        if (job != null && job.getStatus().open()) {
            boolean manual = job.getStatus() == PublishJob.Status.MANUAL;
            String reason = job.getLastError() != null ? job.getLastError()
                    : manual ? "Ninguna vía pudo programarla a tiempo y su hora (" + when + ") ya pasó"
                    : "Su hora (" + when + ") ya pasó y todavía no ha salido";
            String advice = manual ? "Publícala tú a mano con el LEEME" + (job.getManualFile() == null ? "" : " (" + job.getManualFile() + ")")
                    : "Cámbiale la hora (puedes moverla a la siguiente hora libre) o publícala tú a mano";
            return Optional.of(new PublicationOutcome(Result.DELAYED, null, null, route, null, reason, advice));
        }
        return Optional.of(new PublicationOutcome(Result.UNCONFIRMED, null, null, route, null,
                "No hay confirmación de que saliera a su hora (" + when + ")",
                "Si ya la publicaste a mano, ponla en su estado; si no, muévela a la siguiente hora libre"));
    }

    private static PublicationOutcome published(Publication p, @Nullable PublishJob job,
                                                ExternalCalendarRepository.@Nullable Item published, @Nullable String route, ZoneId zone) {
        Instant at;
        AtSource source;
        if (published != null) {
            at = published.at();
            source = AtSource.CALENDAR;
        } else {
            at = p.getPublishedAt();
            source = at == null ? null : AtSource.CONFIRMATION;
        }
        String url = job != null && job.getUrl() != null ? job.getUrl() : published != null ? published.url() : null;
        String flag = p.getFlag();
        boolean late = (flag != null && flag.startsWith(DELAY_PREFIX))
                || (source == AtSource.CALENDAR && at != null && Duration.between(p.scheduledAt(), at).compareTo(DELAY_NOTICE) >= 0);
        if (!late) return new PublicationOutcome(Result.PUBLISHED, at, source, route, url, null, null);
        String reason = flag != null && flag.startsWith(DELAY_PREFIX) ? flag
                : "Salió a las " + HOUR.format(at.atZone(zone)) + ", " + human(Duration.between(p.scheduledAt(), at)) + " después de su hora";
        return new PublicationOutcome(Result.DELAYED, at, source, route, url, reason,
                "Ya está publicada: no tienes que hacer nada. Deja SocialHub abierto a la hora para que salga puntual");
    }

    private static @Nullable String routeOf(@Nullable PublishJob job, ExternalCalendarRepository.@Nullable Item published) {
        if (job != null && job.getRoute() != null) return "A mano".equals(job.getRoute()) ? "Manual" : job.getRoute();
        if (published != null && published.source() != ExternalCalendarRepository.Source.NETWORK) return published.source().label();
        return null;
    }

    /** «2 h 5 min» o «40 min». */
    public static String human(Duration d) {
        long minutes = Math.max(0, d.toMinutes());
        long h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
