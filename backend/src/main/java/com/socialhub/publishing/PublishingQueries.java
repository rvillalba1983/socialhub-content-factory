package com.socialhub.publishing;

import com.socialhub.platform.NotFoundException;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationFile;
import com.socialhub.publishing.domain.PublicationFileRepository;
import com.socialhub.publishing.domain.PublicationFilter;
import com.socialhub.publishing.domain.PublicationPage;
import com.socialhub.publishing.domain.PublicationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@PrimaryPort
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublishingQueries {

    private final PublicationRepository publications;
    private final PublicationFileRepository files;

    public PublicationPage page(PublicationFilter filter, long offset, int limit) {
        return publications.find(filter, offset, limit);
    }

    public List<Publication> all() { return publications.findAll(); }

    /** La publicación existe y es de esa promoción. */
    public boolean belongsTo(String promotionId, String publicationId) {
        return publications.findById(publicationId).map(p -> p.getPromotionId().equals(promotionId)).orElse(false);
    }

    public List<PublicationFile> files(String publicationId) {
        if (!publications.exists(publicationId)) throw new NotFoundException("No existe la publicación " + publicationId);
        return files.files(publicationId);
    }

    public java.util.Optional<Publication> find(String id) { return publications.findById(id); }

    /** Publicaciones de una promoción entre dos fechas (incluidas), por fecha y hora. */
    public List<Publication> between(String promotionId, java.time.LocalDate from, java.time.LocalDate to) {
        return publications.find(new com.socialhub.publishing.domain.PublicationFilter(promotionId, java.util.Set.of(), null,
                from, to, null), 0, 500).items();
    }

    /** Publicaciones entre dos fechas, como datos sencillos para un encargo a Claude. */
    public List<java.util.Map<String, Object>> forAi(String promotionId, java.time.LocalDate from, java.time.LocalDate to) {
        return between(promotionId, from, to).stream().map(PublishingQueries::forAi).toList();
    }

    static java.util.Map<String, Object> forAi(Publication p) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("id", p.getId());
        m.put("platform", p.getPlatform().name());
        m.put("type", p.getType());
        m.put("title", p.getTitle());
        m.put("scheduled_on", p.getScheduledOn().toString());
        m.put("scheduled_time", p.getScheduledTime().toString());
        m.put("status", p.getStatus().name());
        if (p.getNote() != null) m.put("note", p.getNote());
        if (p.getFlag() != null) m.put("flag", p.getFlag());
        if (p.getConfirmation() != null) m.put("confirmation", p.getConfirmation());
        if (p.getCaption() != null) m.put("caption", p.getCaption());
        if (p.getHashtags() != null) m.put("hashtags", p.getHashtags());
        return m;
    }
}
