package com.socialhub.backup.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Ajustes generales de SocialHub (no de una marca), en la base de datos real. */
@SecondaryPort
public interface SettingsRepository {

    String BACKUP_FOLDER = "backup.folder";

    Optional<String> get(String key);

    void put(String key, String value);

    void delete(String key);
}
