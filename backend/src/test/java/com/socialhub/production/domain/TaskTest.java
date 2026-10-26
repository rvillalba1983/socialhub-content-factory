package com.socialhub.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialhub.platform.DomainException;
import com.socialhub.production.ProductionEvents.ReviewApproved;
import com.socialhub.production.ProductionEvents.SwitchedToManual;
import com.socialhub.production.ProductionEvents.TaskCompleted;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private static Task task(TaskStatus status, Agent agent, boolean human, String type, int attempts, List<String> deps) {
        return new Task("t", "gen", "promo", type, "Tarea", status, false, status == TaskStatus.RUNNING ? 50 : 0, agent,
                null, status == TaskStatus.ERROR ? "falló" : null, null, human, attempts, 2, null,
                List.of(new Task.Step("Uno", 1), new Task.Step("Dos", status == TaskStatus.ERROR ? -1 : 0)),
                List.of(new Task.Check("Comprobación", false)), deps, 0);
    }

    private static Task task(TaskStatus status) {
        return task(status, Agent.ai("Sonnet"), false, "generic", 1, List.of());
    }

    @Test
    void runningTaskCanBePausedAndResumed() {
        Task t = task(TaskStatus.RUNNING);
        t.apply(TaskAction.PAUSE, null, NOW);
        assertThat(t.status()).isEqualTo(TaskStatus.WAITING);
        assertThat(t.paused()).isTrue();
        assertThat(t.allowedActions()).contains(TaskAction.RESUME).doesNotContain(TaskAction.PAUSE);

        t.apply(TaskAction.RESUME, null, NOW);
        assertThat(t.status()).isEqualTo(TaskStatus.RUNNING);
        assertThat(t.paused()).isFalse();
        assertThat(t.pendingEvents()).containsExactly("Pausada", "Reanudada");
    }

    @Test
    void actionsNotAllowedInTheCurrentStateAreRejected() {
        Task completed = task(TaskStatus.COMPLETED);
        assertThatThrownBy(() -> completed.apply(TaskAction.PAUSE, null, NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Completada");
        Task blocked = task(TaskStatus.BLOCKED);
        assertThatThrownBy(() -> blocked.apply(TaskAction.RUN_NOW, null, NOW)).isInstanceOf(DomainException.class);
        Task cancelled = task(TaskStatus.CANCELLED);
        assertThatThrownBy(() -> cancelled.apply(TaskAction.CHANGE_AGENT, "Opus", NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void retriesAreLimited() {
        Task t = task(TaskStatus.ERROR);
        assertThat(t.allowedActions()).contains(TaskAction.RETRY);
        t.apply(TaskAction.RETRY, null, NOW);
        assertThat(t.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(t.attempts()).isEqualTo(2);
        assertThat(t.error()).isNull();
        assertThat(t.steps().get(1).state()).isZero();

        Task exhausted = task(TaskStatus.ERROR, Agent.ai("Sonnet"), false, "generic", 2, List.of());
        assertThat(exhausted.allowedActions()).doesNotContain(TaskAction.RETRY).contains(TaskAction.SWITCH_TO_MANUAL);
        assertThatThrownBy(() -> exhausted.apply(TaskAction.RETRY, null, NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void switchingToManualHandsTheTaskToRaul() {
        Task t = task(TaskStatus.ERROR, new Agent("browser", "Publer · Chrome"), false, "generic", 1, List.of());
        t.apply(TaskAction.SWITCH_TO_MANUAL, null, NOW);
        assertThat(t.status()).isEqualTo(TaskStatus.WAITING);
        assertThat(t.human()).isTrue();
        assertThat(t.agent()).isEqualTo(Agent.raul());
        assertThat(t.pendingNotices()).containsExactly(new SwitchedToManual("t", "promo"));
    }

    @Test
    void approvingAReviewCompletesItAndNotifiesPublishing() {
        Task review = task(TaskStatus.WAITING, Agent.raul(), true, Task.TYPE_REVIEW, 1, List.of());
        assertThat(review.allowedActions()).contains(TaskAction.APPROVE).doesNotContain(TaskAction.MARK_DONE);
        review.apply(TaskAction.APPROVE, null, NOW);
        assertThat(review.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(review.checks()).allMatch(Task.Check::passed);
        assertThat(review.pendingNotices()).contains(new ReviewApproved("t", "promo"), new TaskCompleted("t"));
    }

    @Test
    void humanTaskThatIsNotAReviewIsMarkedDone() {
        Task t = task(TaskStatus.WAITING, Agent.raul(), true, "generic", 1, List.of());
        assertThat(t.allowedActions()).contains(TaskAction.MARK_DONE).doesNotContain(TaskAction.APPROVE, TaskAction.CHANGE_AGENT);
        t.apply(TaskAction.MARK_DONE, null, NOW);
        assertThat(t.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void blockedTaskIsQueuedOnlyWhenAllDependenciesAreCompleted() {
        Task t = task(TaskStatus.BLOCKED, Agent.ai("Opus"), false, "generic", 1, List.of("a", "b"));
        assertThat(t.unblockIfReady(Set.of("a"))).isFalse();
        assertThat(t.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(t.unblockIfReady(Set.of("a", "b"))).isTrue();
        assertThat(t.status()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void progressCompletesTheTask() {
        Task t = task(TaskStatus.RUNNING);
        t.advance(30);
        assertThat(t.status()).isEqualTo(TaskStatus.RUNNING);
        assertThat(t.progress()).isEqualTo(80);
        t.advance(30);
        assertThat(t.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(t.progress()).isEqualTo(100);
        assertThat(t.steps()).allMatch(Task.Step::done);
    }

    @Test
    void queuedTasksStartFromTheQueueOnly() {
        Task queued = task(TaskStatus.QUEUED);
        queued.startFromQueue(NOW);
        assertThat(queued.status()).isEqualTo(TaskStatus.RUNNING);
        assertThat(queued.startedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> task(TaskStatus.WAITING).startFromQueue(NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void agentCanOnlyBeChangedToAvailableClaudeModels() {
        Task t = task(TaskStatus.QUEUED);
        t.apply(TaskAction.CHANGE_AGENT, "Opus", NOW);
        assertThat(t.agent()).isEqualTo(Agent.ai("Opus"));
        assertThatThrownBy(() -> t.apply(TaskAction.CHANGE_AGENT, "GPT", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyWaitingTasksCanBePaused() {
        assertThatThrownBy(() -> new Task("t", "gen", null, null, "x", TaskStatus.RUNNING, true, 0, Agent.raul(), null,
                null, null, true, 1, 2, null, List.of(), List.of(), List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
