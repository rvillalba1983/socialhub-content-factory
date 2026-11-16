package com.socialhub.factory.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Una recomendación de «Qué hacer ahora». Se calcula en cada consulta y no se guarda. {@code target} dice a qué pantalla
 * lleva el botón; sin él, la recomendación es solo un texto.
 *
 * @param promotionId promoción a la que se refiere (propuestas retenidas, por revisar o en camino)
 * @param publicationIds solo en {@code CONTENT_MISSING}: las publicaciones sin contenido que pedirá el botón «Pedir contenido ya»
 * @param etaText solo en propuestas en camino: cuándo llegarán, en español
 */
public record Recommendation(String id, Kind kind, String title, @Nullable String detail, @Nullable String promotionId,
                             @Nullable List<String> publicationIds, @Nullable String etaText, @Nullable Target target) {

    /** Por orden de prioridad lo decide quien las calcula; {@code SPEC_WAITING} ya no se calcula y se conserva por compatibilidad. */
    public enum Kind {
        HELD_PROPOSALS, PROPOSALS_TO_REVIEW, BRAND_FOLDERS_MISSING, ORDERS_NOT_COLLECTED, PUBLORA_MISSING,
        BACKUP_FOLDER_MISSING, NO_ACTIVE_PROMOTION, PROPOSALS_PENDING, SPEC_WAITING, PC_TASK_SILENT, NO_PUBLISHING_ROUTE,
        PUBLER_ACCOUNT_MISSING, INSTAGRAM_CONNECTION_EXPIRING, PROMOTION_AUTO_STARTED, CONTENT_MISSING, IDENTITY_MISSING,
        PUBLICATION_TOO_SOON
    }

    /** Pantalla a la que lleva el botón. */
    public enum Screen {
        BRAND_FOLDERS, PROPOSALS, PUBLORA, PUBLER, BACKUPS, NEW_PROMOTION, CLAUDE, INSTAGRAM, BRAND_IDENTITY, PUBLICATIONS
    }

    public record Target(Screen screen, @Nullable String brandId, @Nullable String promotionId) {}

    public Recommendation(String id, Kind kind, String title) {
        this(id, kind, title, null, null, null, null, null);
    }

    public Recommendation detail(@Nullable String detail) {
        return new Recommendation(id, kind, title, detail, promotionId, publicationIds, etaText, target);
    }

    public Recommendation promotionId(@Nullable String promotionId) {
        return new Recommendation(id, kind, title, detail, promotionId, publicationIds, etaText, target);
    }

    public Recommendation publicationIds(@Nullable List<String> publicationIds) {
        return new Recommendation(id, kind, title, detail, promotionId, publicationIds, etaText, target);
    }

    public Recommendation etaText(@Nullable String etaText) {
        return new Recommendation(id, kind, title, detail, promotionId, publicationIds, etaText, target);
    }
}
