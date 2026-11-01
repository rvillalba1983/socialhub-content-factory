package com.socialhub.operations;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface ExternalOperationRepository
        extends JpaRepository<ExternalOperation, Long>, JpaSpecificationExecutor<ExternalOperation> {

    /** Borrado masivo en una sola sentencia (sin cargar las filas). */
    @Transactional
    @Modifying
    @Query("delete from ExternalOperation o where o.startedAt < :limit")
    int deleteOlderThan(Instant limit);
}
