package com.socialhub.publishing;

import com.socialhub.publishing.domain.Publication;
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

    public PublicationPage page(PublicationFilter filter, long offset, int limit) {
        return publications.find(filter, offset, limit);
    }

    public List<Publication> all() { return publications.findAll(); }
}
