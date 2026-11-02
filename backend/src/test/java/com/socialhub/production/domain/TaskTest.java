package com.socialhub.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.DomainException;
import com.socialhub.production.ProductionEvents.ReviewApproved;
import com.socialhub.production.ProductionEvents.SwitchedToManual;
import com.socialhub.production.ProductionEvents.TaskCompleted;
import com.socialhub.production.ProductionEvents.TaskInterrupted;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private static Task task(TaskStatus status, Agent agent, boolean human, String type, int attempts, Set<String> deps) {
        return Task.builder()
                .id("t").stationId("gen").promotionId("promo").type(type).title("Tarea").status(status)
                .progress(status == TaskStatus.RUNNING ? 50 : 0).agent(agent)
                .error(status == TaskStatus.FAILED ? "falló" : null).human(human).attempts(attempts).maxAttempts(2)
                .steps(List.of(new TaskStep("Uno", StepState.DONE),
                        new TaskStep("Dos", status == TaskStatus.FAILED ? StepState.FAILED : StepState.PENDING)))
                .checks(List.of(new TaskCheck("Comprobación", false)))
                .dependsOn(deps)
                .build();
    }

    private static Task task(TaskStatus status) {
        return task(status, Agent.ai(AiModel.SONNET), false, "GENERIC", 1, Set.of());
    }

    @Test
    void runningTaskCanBePausedAndResumed() {
        Task t = task(TaskStatus.RUNNING);
        t.execute(TaskCommandType.PAUSE, null, NOW);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.WAITING);
        assertThat(t.isPaused()).isTrue();
        assertThat(t.allowedCommands()).contains(TaskCommandType.RESUME).doesNotContain(TaskCommandType.PAUSE);

        t.execute(TaskCommandType.RESUME, null, NOW);
        // Vuelve a la cola: el motor la retoma
        assertThat(t.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(t.isPaused()).isFalse();
        assertThat(t.pendingNotices()).containsExactly(new TaskInterrupted("t"));
        assertThat(t.newHistory()).extracting(TaskHistoryEntry::message).containsExactly("Pausada", "Reanudada");
        assertThat(t.getHistory()).hasSize(2);
    }

    @Test
    void commandsNotAllowedInTheCurrentStateAreRejected() {
        Task completed = task(TaskStatus.COMPLETED);
        assertThatThrownBy(() -> completed.execute(TaskCommandType.PAUSE, null, NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Completada");
        Task blocked = task(TaskStatus.BLOCKED);
        assertThatThrownBy(() -> blocked.execute(TaskCommandType.RUN_NOW, null, NOW)).isInstanceOf(DomainException.class);
        Task cancelled = task(TaskStatus.CANCELLED);
        assertThatThrownBy(() -> cancelled.execute(TaskCommandType.CHANGE_AGENT, AiModel.OPUS, NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void retriesAreLimited() {
        Task t = task(TaskStatus.FAILED);
        assertThat(t.allowedCommands()).contains(TaskCommandType.RETRY);
        t.execute(TaskCommandType.RETRY, null, NOW);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(t.getAttempts()).isEqualTo(2);
        assertThat(t.getError()).isNull();
        assertThat(t.getSteps().get(1).state()).isEqualTo(StepState.PENDING);

        Task exhausted = task(TaskStatus.FAILED, Agent.ai(AiModel.SONNET), false, "GENERIC", 2, Set.of());
        assertThat(exhausted.allowedCommands()).doesNotContain(TaskCommandType.RETRY)
                .contains(TaskCommandType.SWITCH_TO_MANUAL);
        assertThatThrownBy(() -> exhausted.execute(TaskCommandType.RETRY, null, NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void switchingToManualHandsTheTaskToRaul() {
        Task t = task(TaskStatus.FAILED, new Agent(AgentKind.BROWSER, "Publer · Chrome"), false, "GENERIC", 1, Set.of());
        t.execute(TaskCommandType.SWITCH_TO_MANUAL, null, NOW);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.WAITING);
        assertThat(t.isHuman()).isTrue();
        assertThat(t.getAgent()).isEqualTo(Agent.raul());
        assertThat(t.pendingNotices()).containsExactly(new SwitchedToManual("t", "promo"));
    }

    @Test
    void approvingAReviewCompletesItAndNotifiesPublishing() {
        Task review = task(TaskStatus.WAITING, Agent.raul(), true, Task.TYPE_REVIEW, 1, Set.of());
        assertThat(review.allowedCommands()).contains(TaskCommandType.APPROVE).doesNotContain(TaskCommandType.MARK_DONE);
        review.execute(TaskCommandType.APPROVE, null, NOW);
        assertThat(review.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(review.getChecks()).allMatch(TaskCheck::passed);
        assertThat(review.pendingNotices()).contains(new ReviewApproved("t", "promo"), new TaskCompleted("t"));
    }

    @Test
    void humanTaskThatIsNotAReviewIsMarkedDone() {
        Task t = task(TaskStatus.WAITING, Agent.raul(), true, "GENERIC", 1, Set.of());
        assertThat(t.allowedCommands()).contains(TaskCommandType.MARK_DONE)
                .doesNotContain(TaskCommandType.APPROVE, TaskCommandType.CHANGE_AGENT);
        t.execute(TaskCommandType.MARK_DONE, null, NOW);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void blockedTaskIsQueuedOnlyWhenAllDependenciesAreCompleted() {
        Task t = task(TaskStatus.BLOCKED, Agent.ai(AiModel.OPUS), false, "GENERIC", 1, Set.of("a", "b"));
        assertThat(t.unblockIfReady(Set.of("a"), NOW)).isFalse();
        assertThat(t.getStatus()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(t.unblockIfReady(Set.of("a", "b"), NOW)).isTrue();
        assertThat(t.getStatus()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void progressCompletesTheTask() {
        Task t = task(TaskStatus.RUNNING);
        t.advance(30, NOW);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(t.getProgress()).isEqualTo(80);
        assertThat(t.getSteps().get(1).state()).isEqualTo(StepState.IN_PROGRESS);
        t.advance(30, NOW);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(t.getProgress()).isEqualTo(100);
        assertThat(t.getSteps()).allMatch(s -> s.state() == StepState.DONE);
    }

    @Test
    void queuedTasksStartFromTheQueueOnly() {
        Task queued = task(TaskStatus.QUEUED);
        queued.startFromQueue(NOW);
        assertThat(queued.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(queued.getStartedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> task(TaskStatus.WAITING).startFromQueue(NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void changingTheAgentNeedsAModel() {
        Task t = task(TaskStatus.QUEUED);
        t.execute(TaskCommandType.CHANGE_AGENT, AiModel.OPUS, NOW);
        assertThat(t.getAgent()).isEqualTo(Agent.ai(AiModel.OPUS));
        assertThat(t.getAgent().name()).isEqualTo("Opus");
        assertThatThrownBy(() -> t.execute(TaskCommandType.CHANGE_AGENT, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyWaitingTasksCanBePaused() {
        assertThatThrownBy(() -> Task.builder().id("t").stationId("gen").title("x").status(TaskStatus.RUNNING)
                .paused(true).agent(Agent.raul()).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theEngineFinishesOrFailsARun() {
        Task ok = task(TaskStatus.RUNNING);
        ok.reportProgress(150);
        assertThat(ok.getProgress()).isEqualTo(99);
        ok.finishRun("5 piezas", NOW);
        assertThat(ok.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(ok.getResult()).isEqualTo("5 piezas");

        Task ko = task(TaskStatus.RUNNING);
        ko.execute(TaskCommandType.PAUSE, null, NOW);
        ko.execute(TaskCommandType.RESUME, null, NOW);
        ko.startFromQueue(NOW);
        ko.failRun("FFmpeg terminó con código 1", NOW);
        assertThat(ko.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(ko.getError()).isEqualTo("FFmpeg terminó con código 1");
        assertThat(ko.allowedCommands()).contains(TaskCommandType.RETRY);
        assertThatThrownBy(() -> ko.finishRun("tarde", NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void cancellingARunningTaskInterruptsIt() {
        Task t = task(TaskStatus.RUNNING);
        t.execute(TaskCommandType.CANCEL, null, NOW);
        assertThat(t.pendingNotices()).containsExactly(new TaskInterrupted("t"));
        Task queued = task(TaskStatus.QUEUED);
        queued.execute(TaskCommandType.CANCEL, null, NOW);
        assertThat(queued.pendingNotices()).isEmpty();
    }

    @Test
    void aRestartPutsRunningTasksBackInTheQueueWithoutSpendingAnAttempt() {
        Task t = task(TaskStatus.RUNNING);
        assertThat(t.recoverAfterRestart(NOW)).isTrue();
        assertThat(t.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(t.getAttempts()).isEqualTo(1);
        assertThat(task(TaskStatus.WAITING).recoverAfterRestart(NOW)).isFalse();
    }

    @Test
    void periodicChecksAreRequeued() {
        Task t = task(TaskStatus.FAILED);
        assertThat(t.requeue(NOW)).isTrue();
        assertThat(t.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(t.getError()).isNull();
        assertThat(task(TaskStatus.RUNNING).requeue(NOW)).isFalse();
    }

    @Test
    void aMissingFolderBlocksWithoutSpendingAnAttemptAndRequeuesWhenItAppears() {
        Task t = task(TaskStatus.RUNNING);
        t.blockRun("Falta G:", "G:\\Mi unidad", NOW);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(t.getWaitingFor()).isEqualTo("G:\\Mi unidad");
        assertThat(t.getAttempts()).isEqualTo(1);
        assertThat(t.resourceAvailable(NOW)).isTrue();
        assertThat(t.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(t.getWaitingFor()).isNull();
        assertThat(t.resourceAvailable(NOW)).isFalse();

        // Raúl pide comprobarlo ya: vuelve a la cola (si sigue sin estar, se bloqueará otra vez)
        Task again = task(TaskStatus.RUNNING);
        again.blockRun("Falta G:", "G:", NOW);
        again.execute(TaskCommandType.RECHECK, null, NOW);
        assertThat(again.getStatus()).isEqualTo(TaskStatus.QUEUED);

        Task cancelled = task(TaskStatus.RUNNING);
        cancelled.blockRun("Falta G:", "G:", NOW);
        cancelled.execute(TaskCommandType.CANCEL, null, NOW);
        assertThat(cancelled.getWaitingFor()).isNull();
    }
}
