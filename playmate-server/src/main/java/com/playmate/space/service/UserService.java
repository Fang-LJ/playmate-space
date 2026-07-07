package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.UnauthorizedException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.dto.user.UpdateUserProfileRequest;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.UserMapper;
import com.playmate.space.vo.CurrentUserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class UserService {

    private static final String USER_STATUS_DISABLED = "DISABLED";

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public CurrentUserResponse getCurrentUser() {
        return buildResponse(loadCurrentActiveUser());
    }

    @Transactional
    public CurrentUserResponse updateCurrentUser(UpdateUserProfileRequest request) {
        UserEntity user = loadCurrentActiveUser();
        UserEntity updateEntity = new UserEntity();
        updateEntity.setId(user.getId());
        boolean changed = false;

        if (request.getNickname() != null) {
            String nickname = request.getNickname().trim();
            if (!StringUtils.hasText(nickname)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "昵称不能为空");
            }
            updateEntity.setNickname(nickname);
            changed = true;
        }
        if (request.getAvatarUrl() != null) {
            updateEntity.setAvatarUrl(request.getAvatarUrl().trim());
            changed = true;
        }
        if (request.getPhone() != null) {
            updateEntity.setPhone(request.getPhone().trim());
            changed = true;
        }

        if (changed) {
            updateEntity.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(updateEntity);
            if (updateEntity.getNickname() != null) {
                user.setNickname(updateEntity.getNickname());
            }
            if (updateEntity.getAvatarUrl() != null) {
                user.setAvatarUrl(updateEntity.getAvatarUrl());
            }
            if (updateEntity.getPhone() != null) {
                user.setPhone(updateEntity.getPhone());
            }
            user.setUpdateTime(updateEntity.getUpdateTime());
        }
        return buildResponse(user);
    }

    private UserEntity loadCurrentActiveUser() {
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
        if (USER_STATUS_DISABLED.equals(user.getStatus())) {
            throw new ForbiddenException("用户已被禁用");
        }
        return user;
    }

    private CurrentUserResponse buildResponse(UserEntity user) {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setPhone(user.getPhone());
        response.setStatus(user.getStatus());
        response.setCreateTime(user.getCreateTime());
        response.setLastLoginTime(user.getLastLoginTime());
        return response;
    }
}
