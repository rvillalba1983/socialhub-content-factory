package com.socialhub.publishing.domain;

import java.time.Instant;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Encargos a Claude que SocialHub pidió solo, para no repetirlos. */
@SecondaryPort
public interface AutoRequestRepository {

    boolean exists(String publicationId, String kind, String fingerprint);

    void add(String publicationId, String kind, String fingerprint, String taskId, Instant at);
}
