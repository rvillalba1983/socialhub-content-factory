package com.socialhub.publishing.application;

import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.publishing.PublicationCitations;
import com.socialhub.publishing.domain.Citation;
import com.socialhub.publishing.domain.CitationRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class CitationService implements PublicationCitations {

    private final PublicationRepository publications;
    private final CitationRepository citations;
    private final ApplicationEventPublisher events;

    @Transactional
    @Override
    public void declare(String publicationId, List<Declared> declared) {
        if (declared.size() > MAX_PER_PUBLICATION) {
            throw new IllegalArgumentException("Demasiadas citas en una publicación (máximo " + MAX_PER_PUBLICATION + ")");
        }
        if (publications.findById(publicationId).isEmpty()) throw new NotFoundException("No existe la publicación " + publicationId);
        citations.replace(publicationId, declared.stream().map(d -> new Citation(d.text(), d.sourceFile(), d.location())).toList());
        events.publishEvent(new FactoryChanged("publication", publicationId));
    }
}
