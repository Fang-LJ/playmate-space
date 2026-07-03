package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.activity.ActivityDetailResponse;
import com.playmate.space.dto.activity.ActivityListItemResponse;
import com.playmate.space.dto.activity.CreateActivityRequest;
import com.playmate.space.dto.activity.CreateActivityResponse;
import com.playmate.space.dto.activity.UpdateActivityRequest;
import com.playmate.space.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    public ApiResponse<CreateActivityResponse> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.success(activityService.createActivity(request));
    }

    @GetMapping
    public ApiResponse<List<ActivityListItemResponse>> listMyActivities() {
        return ApiResponse.success(activityService.listMyActivities());
    }

    @GetMapping("/{activityId}")
    public ApiResponse<ActivityDetailResponse> getActivityDetail(@PathVariable Long activityId) {
        return ApiResponse.success(activityService.getActivityDetail(activityId));
    }

    @PutMapping("/{activityId}")
    public ApiResponse<ActivityDetailResponse> updateActivity(
            @PathVariable Long activityId,
            @Valid @RequestBody UpdateActivityRequest request
    ) {
        return ApiResponse.success(activityService.updateActivity(activityId, request));
    }

    @PostMapping("/{activityId}/end")
    public ApiResponse<ActivityDetailResponse> endActivity(@PathVariable Long activityId) {
        return ApiResponse.success(activityService.endActivity(activityId));
    }

    @PostMapping("/{activityId}/cancel")
    public ApiResponse<ActivityDetailResponse> cancelActivity(@PathVariable Long activityId) {
        return ApiResponse.success(activityService.cancelActivity(activityId));
    }
}
