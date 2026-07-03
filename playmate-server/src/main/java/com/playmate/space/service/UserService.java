package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.exception.UnauthorizedException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.UserMapper;
import com.playmate.space.vo.CurrentUserResponse;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public CurrentUserResponse getCurrentUser() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException();
        }

        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getId, userId)
                .eq(UserEntity::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (user == null) {
            throw new UnauthorizedException("用户不存在或登录已失效");
        }

        CurrentUserResponse response = new CurrentUserResponse();
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setStatus(user.getStatus());
        response.setLastLoginTime(user.getLastLoginTime());
        return response;
    }
}
