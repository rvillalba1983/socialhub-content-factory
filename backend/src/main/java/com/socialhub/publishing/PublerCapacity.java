package com.socialhub.publishing;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PublerAccountRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Límite del plan gratuito de Publer: un máximo de publicaciones programadas a la vez por cuenta (10 por defecto,
 * {@code socialhub.publer.scheduled-limit}). Las cuentas se cuentan por nombre y red, sumando marcas: la de TikTok la
 * usan las dos marcas, así que sus publicaciones comparten el mismo tope.
 * <p>
 * Cuenta las que SocialHub tiene programadas en Publer y aún no han salido, las canceladas que siguen allí (con su aviso «Estaba programada en Publer») y los encargos «Programar en Publer» en
 * curso (que acabarán ocupando un hueco). No ve lo que Raúl haya programado en Publer por su cuenta.
 */
@Component
class PublerCapacity {

    private final PublishJobRepository jobs;
    private final PublicationRepository publications;
    private final PromotionQueries promotions;
    private final PublerAccountRepository accounts;
    private final int limit;

    PublerCapacity(PublishJobRepository jobs, PublicationRepository publications, PromotionQueries promotions,
                   PublerAccountRepository accounts, @Value("${socialhub.publer.scheduled-limit:10}") int limit) {
        this.jobs = jobs;
        this.publications = publications;
        this.promotions = promotions;
        this.accounts = accounts;
        this.limit = limit;
    }

    int limit() { return limit; }

    /** Cuántas publicaciones ocupan hueco en esa cuenta y red (sin contar {@code except}). */
    int used(String accountName, Platform platform, String except, Instant now) {
        Set<String> brands = new HashSet<>(accounts.brandsUsing(accountName, platform));
        int n = 0;
        for (PublishJob job : jobs.withStatus(EnumSet.of(PublishJob.Status.SCHEDULED, PublishJob.Status.WORKING, PublishJob.Status.CANCELLED))) {
            if (job.getPublicationId().equals(except) || !"Publer".equals(job.getRoute())) continue;
            // En curso solo cuenta si ya hay encargo a Claude; programada, si aún no ha llegado su hora
            if (job.getStatus() == PublishJob.Status.WORKING && job.getTaskId() == null) continue;
            var p = publications.findById(job.getPublicationId()).orElse(null);
            if (p == null || p.getPlatform() != platform) continue;
            if (job.getStatus() != PublishJob.Status.WORKING && !p.scheduledAt().isAfter(now)) continue;
            // Cancelada pero que sigue en Publer hasta que Raúl la borre allí: ocupa hueco. (Si Raúl la vuelve a programar,
            // el trabajo nuevo sustituye a este y el hueco lo cuenta el nuevo.)
            if (job.getStatus() == PublishJob.Status.CANCELLED
                    && (p.getFlag() == null || !p.getFlag().startsWith(PublishingEngine.STILL_SCHEDULED + "Publer"))) continue;
            if (brands.contains(promotions.brandOf(p.getPromotionId()))) n++;
        }
        return n;
    }

    boolean full(String accountName, Platform platform, String except, Instant now) {
        return used(accountName, platform, except, now) >= limit;
    }
}
