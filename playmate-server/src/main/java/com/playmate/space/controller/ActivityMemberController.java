package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.member.ActivityMemberResponse;
import com.playmate.space.dto.member.UpdateMyNicknameRequest;
import com.playmate.space.service.ActivityMemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activities/{activityId}/members")
public class ActivityMemberController {

    private final ActivityMemberService activityMemberService;

    public ActivityMemberController(ActivityMemberService activityMemberService) {
        this.activityMemberService = activityMemberService;
    }

    @GetMapping
    public ApiResponse<List<ActivityMemberResponse>> listMembers(@PathVariable Long activityId) {
        return ApiResponse.success(activityMemberService.listMembers(activityId));
    }

    @PutMapping("/me/nickname")
    public ApiResponse<ActivityMemberResponse> updateMyNickname(
            @PathVariable Long activityId,
            @Valid @RequestBody UpdateMyNicknameRequest request
    ) {
        return ApiResponse.success(activityMemberService.updateMyNickname(activityId, request));
    }

    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long activityId,
            @PathVariable Long memberId
    ) {
        activityMemberService.removeMember(activityId, memberId);
        return ApiResponse.success(null);
    }
}
