package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.todo.CreateActivityReminderRequest;
import com.playmate.space.dto.todo.ReminderAckStatusResponse;
import com.playmate.space.service.ActivityTodoLifecycleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class ActivityTodoController {
    private final ActivityTodoLifecycleService lifecycleService;
    public ActivityTodoController(ActivityTodoLifecycleService lifecycleService) { this.lifecycleService = lifecycleService; }
    @PostMapping("/api/activities/{activityId}/reminders")
    public ApiResponse<Long> createReminder(@PathVariable Long activityId, @Valid @RequestBody CreateActivityReminderRequest request) {
        return ApiResponse.success(lifecycleService.publishManualReminder(activityId, request));
    }
    @PostMapping("/api/activity-todos/{todoId}/ack")
    public ApiResponse<Void> acknowledge(@PathVariable Long todoId) { lifecycleService.acknowledgeReminder(todoId); return ApiResponse.success(null); }
    @GetMapping("/api/activities/{activityId}/reminders/{todoId}/ack-status")
    public ApiResponse<ReminderAckStatusResponse> ackStatus(@PathVariable Long activityId, @PathVariable Long todoId) {
        return ApiResponse.success(lifecycleService.getReminderAckStatus(activityId, todoId));
    }
}
