package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.collaboration.UserActivityTodosResponse;
import com.playmate.space.service.ActivityTodoService;
import com.playmate.space.dto.user.UpdateUserProfileRequest;
import com.playmate.space.dto.user.UpdateMyAccountRequest;
import com.playmate.space.dto.user.WechatPhoneBindRequest;
import com.playmate.space.service.UserService;
import com.playmate.space.vo.CurrentUserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ActivityTodoService activityTodoService;

    public UserController(UserService userService, ActivityTodoService activityTodoService) {
        this.userService = userService;
        this.activityTodoService = activityTodoService;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.success(userService.getCurrentUser());
    }

    @GetMapping("/me/activity-todos")
    public ApiResponse<UserActivityTodosResponse> activityTodos() {
        return ApiResponse.success(activityTodoService.getCurrentUserTodos());
    }

    @PutMapping("/me")
    public ApiResponse<CurrentUserResponse> updateMe(@Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success(userService.updateCurrentUser(request));
    }

    @PutMapping("/me/account")
    public ApiResponse<CurrentUserResponse> updateMyAccount(@Valid @RequestBody UpdateMyAccountRequest request) {
        return ApiResponse.success(userService.updateMyAccount(request));
    }

    @PostMapping("/me/wechat-phone")
    public ApiResponse<CurrentUserResponse> bindWechatPhone(@Valid @RequestBody WechatPhoneBindRequest request) {
        return ApiResponse.success(userService.bindWechatPhone(request));
    }
}
