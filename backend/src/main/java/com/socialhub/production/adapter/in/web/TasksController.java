package com.socialhub.production.adapter.in.web;

import com.socialhub.api.TasksApi;
import com.socialhub.api.model.TaskCommandDto;
import com.socialhub.api.model.TaskDto;
import com.socialhub.api.model.TaskListDto;
import com.socialhub.api.model.TaskStatusDto;
import com.socialhub.production.ProductionQueries;
import com.socialhub.production.ProductionService;
import com.socialhub.production.domain.TaskCommandType;
import com.socialhub.production.domain.TaskFilter;
import com.socialhub.production.domain.TaskStatus;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class TasksController implements TasksApi {

    private final ProductionService service;
    private final ProductionQueries queries;
    private final ProductionWebMapper mapper;

    @Override
    public ResponseEntity<TaskListDto> listTasks(@Nullable String stationId, @Nullable String promotionId,
                                                 @Nullable List<TaskStatusDto> status, @Nullable Boolean human) {
        var statuses = status == null ? java.util.Set.<TaskStatus>of()
                : status.stream().map(s -> TaskStatus.valueOf(s.name())).collect(Collectors.toUnmodifiableSet());
        var tasks = queries.tasks(new TaskFilter(stationId, promotionId, statuses, human));
        return ResponseEntity.ok(new TaskListDto(tasks.stream().map(mapper::toDto).toList()));
    }

    @Override
    public ResponseEntity<TaskDto> getTask(String taskId) {
        return ResponseEntity.ok(mapper.toDto(queries.task(taskId)));
    }

    @Override
    public ResponseEntity<TaskDto> executeTaskCommand(String taskId, String requestGuard, TaskCommandDto command) {
        var task = service.execute(taskId, TaskCommandType.valueOf(command.getType().name()),
                mapper.toDomain(command.getModel()));
        return ResponseEntity.ok(mapper.toDto(task));
    }
}
