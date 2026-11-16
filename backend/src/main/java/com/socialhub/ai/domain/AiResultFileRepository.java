package com.socialhub.ai.domain;

import java.time.Instant;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Registro de los archivos de resultado ya leídos. */
@SecondaryPort
public interface AiResultFileRepository {

    boolean alreadyRead(String path, Instant modifiedAt);

    void save(AiResultFile file);
}
