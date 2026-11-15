package com.socialhub.ai.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiResultFileJpaRepository extends JpaRepository<AiResultFileEntity, Long> {

    boolean existsByPathAndModifiedAt(String path, Instant modifiedAt);
}
