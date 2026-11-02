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
}
