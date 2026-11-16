package com.socialhub.operations.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface ExternalOperationJpaRepository
        extends JpaRepository<ExternalOperationEntity, Long>, JpaSpecificationExecutor<ExternalOperationEntity> {

    /** Borrado masivo en una sola sentencia (sin cargar las filas). */
    @Transactional
    @Modifying
    @Query("delete from ExternalOperationEntity o where o.startedAt < :limit")
    int deleteOlderThan(Instant limit);
}
