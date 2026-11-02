package com.socialhub.ai;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiResultFileRepository extends JpaRepository<AiResultFileEntity, Long> {
    boolean existsByPathAndModifiedAt(String path, Instant modifiedAt);
}
