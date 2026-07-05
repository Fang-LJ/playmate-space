package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.activity.ActivityInviteInfoResponse;
import com.playmate.space.dto.activity.JoinActivityResponse;
import com.playmate.space.service.ActivityInviteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity-invites")
public class ActivityInviteController {

    private final ActivityInviteService activityInviteService;

    public ActivityInviteController(ActivityInviteService activityInviteService) {
        this.activityInviteService = activityInviteService;
    }

    @GetMapping("/{shareCode}")
    public ApiResponse<ActivityInviteInfoResponse> getInviteInfo(
            @PathVariable String shareCode,
            HttpServletRequest request
    ) {
        return ApiResponse.success(activityInviteService.getInviteInfo(shareCode, request));
    }

    @PostMapping("/{shareCode}/join")
    public ApiResponse<JoinActivityResponse> joinActivity(
            @PathVariable String shareCode,
            HttpServletRequest request
    ) {
        return ApiResponse.success(activityInviteService.joinActivity(shareCode, request));
    }
}
