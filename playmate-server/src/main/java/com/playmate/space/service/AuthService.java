package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.security.JwtUtils;
import com.playmate.space.dto.LoginRequest;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.UserMapper;
import com.playmate.space.vo.LoginResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final String USER_STATUS_NORMAL = "NORMAL";
    private static final String USER_STATUS_DISABLED = "DISABLED";

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final WechatLoginService wechatLoginService;

    public AuthService(UserMapper userMapper, JwtUtils jwtUtils, WechatLoginService wechatLoginService) {
        this.userMapper = userMapper;
        this.jwtUtils = jwtUtils;
        this.wechatLoginService = wechatLoginService;
    }

    @Transactional
    public LoginResponse wxLogin(LoginRequest request) {
        WechatLoginService.WechatSession session = wechatLoginService.resolveSession(request);
        if (session == null || !StringUtils.hasText(session.openid())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "openid 获取失败");
        }

        UserEntity user = findByOpenid(session.openid());
        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            user = createUser(request, session, now);
        } else {
            ensureUserEnabled(user);
            updateLoginInfo(user, request, now);
        }

        return buildLoginResponse(user);
    }

    private UserEntity findByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getOpenid, openid)
                .eq(UserEntity::getDeleteFlag, 0)
                .last("LIMIT 1"));
    }

    private UserEntity createUser(LoginRequest request, WechatLoginService.WechatSession session, LocalDateTime now) {
        UserEntity user = new UserEntity();
        user.setOpenid(session.openid());
        user.setUnionid(session.unionid());
        user.setNickname(resolveNullableText(request == null ? null : request.getNickname()));
        user.setAvatarUrl(resolveNullableText(request == null ? null : request.getAvatarUrl()));
        user.setStatus(USER_STATUS_NORMAL);
        user.setLastLoginTime(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setDeleteFlag(0);
        userMapper.insert(user);
        return user;
    }

    private void updateLoginInfo(UserEntity user, LoginRequest request, LocalDateTime now) {
        boolean changed = false;
        if (StringUtils.hasText(request == null ? null : request.getNickname())) {
            user.setNickname(request.getNickname().trim());
            changed = true;
        }
        if (StringUtils.hasText(request == null ? null : request.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
            changed = true;
        }
        user.setLastLoginTime(now);
        user.setUpdateTime(now);
        changed = true;

        if (changed) {
            userMapper.updateById(user);
        }
    }

    private void ensureUserEnabled(UserEntity user) {
        if (USER_STATUS_DISABLED.equals(user.getStatus())) {
            throw new ForbiddenException("用户已被禁用");
        }
    }

    private LoginResponse buildLoginResponse(UserEntity user) {
        LoginResponse response = new LoginResponse();
        response.setToken(jwtUtils.generateToken(user.getId()));
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setNeedCompleteProfile(!StringUtils.hasText(user.getNickname())
                || !StringUtils.hasText(user.getAvatarUrl()));
        return response;
    }

    private String resolveNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
