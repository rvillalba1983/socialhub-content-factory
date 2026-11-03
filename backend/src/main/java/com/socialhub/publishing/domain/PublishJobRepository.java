package com.socialhub.publishing.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Tareas de publicación (una por publicación). */
@SecondaryPort
public interface PublishJobRepository {

    Optional<PublishJob> find(String publicationId);

    List<PublishJob> withStatus(Collection<PublishJob.Status> statuses);

    /** Guarda; falla si otro la cambió entre medias (versión distinta). */
    void save(PublishJob job);
}
