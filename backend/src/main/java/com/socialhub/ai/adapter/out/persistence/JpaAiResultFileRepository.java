package com.socialhub.ai.adapter.out.persistence;

import com.socialhub.ai.domain.AiResultFile;
import com.socialhub.ai.domain.AiResultFileRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaAiResultFileRepository implements AiResultFileRepository {

    private final AiResultFileJpaRepository jpa;

    @Override
    public boolean alreadyRead(String path, Instant modifiedAt) { return jpa.existsByPathAndModifiedAt(path, modifiedAt); }

    @Override
    public void save(AiResultFile file) { jpa.save(new AiResultFileEntity(file)); }
}
