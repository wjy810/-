package com.jobproof.modules.task.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskView> get(@PathVariable String id) {
        return ApiResponse.ok(taskService.getOwned(SecurityConfig.currentAccount().accountId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<TaskView> cancel(@PathVariable String id) {
        return ApiResponse.ok(taskService.cancel(SecurityConfig.currentAccount().accountId(), id));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<TaskView> retry(@PathVariable String id) {
        return ApiResponse.ok(taskService.retryManually(SecurityConfig.currentAccount().accountId(), id));
    }
}
