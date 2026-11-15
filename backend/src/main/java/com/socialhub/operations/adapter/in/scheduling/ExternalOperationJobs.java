package com.socialhub.operations.adapter.in.scheduling;

import com.socialhub.operations.RecordExternalOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Las tareas periódicas del registro: guardar por lotes y borrar lo antiguo. */
@Component
@RequiredArgsConstructor
class ExternalOperationJobs {

    private final RecordExternalOperations operations;

    @Scheduled(fixedDelayString = "${socialhub.operations.flush-millis:2000}")
    void flush() { operations.flush(); }

    @Scheduled(cron = "${socialhub.operations.purge-cron:0 30 3 * * *}")
    void purge() { operations.purge(); }
}
