package com.playmate.space.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ApiResponse;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final UserMapper userMapper;

    public HealthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeleteFlag, 0));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("database", "UP");
        data.put("userCount", userCount);
        data.put("time", LocalDateTime.now().toString());
        return ApiResponse.success(data);
    }
}
