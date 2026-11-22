package com.socialhub.publishing;

import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationStatus;
import java.nio.file.Path;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Alta, edición y cambios de estado de las publicaciones y de sus archivos. */
@PrimaryPort
public interface PublishingCommands {

    /** Alta desde el centro de control. El identificador es la fecha y la plataforma: «2026-11-10-instagram». */
    Publication create(String promotionId, Publication.Details details, PublicationStatus status);

    Publication update(String id, Publication.Details details);

    Publication changeStatus(String id, PublicationStatus status);

    /** Cambio de estado. Para cancelar hace falta el motivo, que queda en la memoria editorial. */
    Publication changeStatus(String id, PublicationStatus status, com.socialhub.publishing.domain.@Nullable RejectionReason reason, @Nullable String note);

    /** Raúl da por resuelta la incidencia anotada. */
    void clearFlag(String id);

    /** Asocia a mano un archivo de las carpetas de la promoción. */
    PublicationFile attachManually(String publicationId, String path);

    /** Asocia los archivos generados para una pieza del spec.json. Los que ya estaban se dejan como están. */
    int attachGenerated(String promotionId, String publicationId, List<Path> generated);

    /** Quita la asociación. El archivo no se toca. */
    void detach(String publicationId, String path);

    /** Texto y hashtags de una publicación, para guardarlos juntos. */
    record Texts(String publicationId, String caption, String hashtags) {}

    /** Qué pasó con los textos guardados: cuáles no se aplicaron y cuáles sustituyeron un texto de Raúl. */
    record TextsOutcome(List<String> kept, List<String> replaced) {}

    /**
     * Guarda los textos propuestos. Comprueba todas las publicaciones antes de cambiar ninguna. Si la publicación ya
     * tiene un texto de Raúl (escrito o corregido) y el encargo lo hizo SocialHub solo, no se toca: el de Claude queda en
     * la incidencia de la publicación para que lo copie si quiere. Si lo pidió Raúl a mano ({@code replaceOwn}), el de
     * Claude lo sustituye y el de Raúl queda guardado para recuperarlo.
     */
    TextsOutcome applyTexts(String promotionId, List<Texts> texts, boolean replaceOwn);

    /** Raúl recupera el texto suyo que sustituyó Claude. */
    Publication restorePreviousText(String id);

    /** Raúl descarta el texto de la publicación con su motivo: queda en la memoria editorial. */
    Publication discardTexts(String id, com.socialhub.publishing.domain.RejectionReason reason, @Nullable String note);

    /**
     * Mueve la publicación a la hora que Raúl ha confirmado. Vuelve a calcular la siguiente hora libre y falla si ya no es
     * esa (otra publicación ha ocupado el hueco o ha pasado el tiempo); nunca mueve sin {@code confirmed}.
     *
     * @param acceptAfterEnd Raúl acepta que quede después de la fecha final de la promoción
     */
    Publication moveToFreeSlot(String id, java.time.LocalDate day, java.time.LocalTime time, boolean confirmed, boolean acceptAfterEnd);
}
