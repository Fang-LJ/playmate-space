package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.UnauthorizedException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.dto.user.UpdateMyAccountRequest;
import com.playmate.space.dto.user.UpdateUserProfileRequest;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.UserMapper;
import com.playmate.space.vo.CurrentUserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class UserService {

    private static final String USER_STATUS_DISABLED = "DISABLED";
    private static final Set<String> ALLOWED_GENDERS = Set.of("UNKNOWN", "MALE", "FEMALE", "OTHER");

    private final UserMapper userMapper;
    private final PasswordService passwordService;

    public UserService(UserMapper userMapper, PasswordService passwordService) {
        this.userMapper = userMapper;
        this.passwordService = passwordService;
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
            String nickname = normalizeNullableText(request.getNickname());
            if (!StringUtils.hasText(nickname)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "昵称不能为空");
            }
            updateEntity.setNickname(nickname);
            user.setNickname(nickname);
            changed = true;
        }
        if (request.getAvatarUrl() != null) {
            String avatarUrl = normalizeNullableText(request.getAvatarUrl());
            updateEntity.setAvatarUrl(avatarUrl);
            user.setAvatarUrl(avatarUrl);
            changed = true;
        }
        if (request.getPhone() != null) {
            String phone = normalizeNullableText(request.getPhone());
            assertPhoneNotUsed(phone, user.getId());
            updateEntity.setPhone(phone);
            user.setPhone(phone);
            changed = true;
        }
        if (request.getEmail() != null) {
            String email = normalizeNullableText(request.getEmail());
            assertEmailNotUsed(email, user.getId());
            updateEntity.setEmail(email);
            user.setEmail(email);
            changed = true;
        }
        if (request.getGender() != null) {
            String gender = normalizeNullableText(request.getGender());
            if (!StringUtils.hasText(gender)) {
                gender = "UNKNOWN";
            }
            validateGender(gender);
            updateEntity.setGender(gender);
            user.setGender(gender);
            changed = true;
        }
        if (request.getAddress() != null) {
            String address = normalizeNullableText(request.getAddress());
            updateEntity.setAddress(address);
            user.setAddress(address);
            changed = true;
        }
        if (request.getBio() != null) {
            String bio = normalizeNullableText(request.getBio());
            updateEntity.setBio(bio);
            user.setBio(bio);
            changed = true;
        }

        if (changed) {
            updateEntity.setProfileCompleted(resolveProfileCompleted(user));
            updateEntity.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(updateEntity);
            user.setProfileCompleted(updateEntity.getProfileCompleted());
            user.setUpdateTime(updateEntity.getUpdateTime());
        }
        return buildResponse(user);
    }

    @Transactional
    public CurrentUserResponse updateMyAccount(UpdateMyAccountRequest request) {
        UserEntity user = loadCurrentActiveUser();
        String phone = request.getPhone() == null ? null : normalizeNullableText(request.getPhone());
        String email = request.getEmail() == null ? null : normalizeNullableText(request.getEmail());
        String password = request.getPassword();
        if (!StringUtils.hasText(phone) && !StringUtils.hasText(email) && !StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "请至少填写手机号、邮箱或密码");
        }

        UserEntity updateEntity = new UserEntity();
        updateEntity.setId(user.getId());
        if (request.getPhone() != null) {
            assertPhoneNotUsed(phone, user.getId());
            updateEntity.setPhone(phone);
            user.setPhone(phone);
        }
        if (request.getEmail() != null) {
            assertEmailNotUsed(email, user.getId());
            updateEntity.setEmail(email);
            user.setEmail(email);
        }
        if (StringUtils.hasText(password)) {
            updateEntity.setPasswordHash(passwordService.hashPassword(password));
            updateEntity.setPasswordSet(1);
            user.setPasswordSet(1);
        }
        updateEntity.setProfileCompleted(resolveProfileCompleted(user));
        updateEntity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(updateEntity);
        user.setProfileCompleted(updateEntity.getProfileCompleted());
        user.setUpdateTime(updateEntity.getUpdateTime());
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

    private void assertPhoneNotUsed(String phone, Long currentUserId) {
        if (!StringUtils.hasText(phone)) {
            return;
        }
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, phone)
                .eq(UserEntity::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "手机号已被使用");
        }
    }

    private void assertEmailNotUsed(String email, Long currentUserId) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)
                .eq(UserEntity::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "邮箱已被使用");
        }
    }

    private void validateGender(String gender) {
        if (!ALLOWED_GENDERS.contains(gender)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "性别取值不合法");
        }
    }

    private Integer resolveProfileCompleted(UserEntity user) {
        return isProfileComplete(user) ? 1 : 0;
    }

    private boolean isProfileComplete(UserEntity user) {
        return StringUtils.hasText(user.getNickname()) && StringUtils.hasText(user.getAvatarUrl());
    }

    private boolean isAccountProtected(UserEntity user) {
        return Integer.valueOf(1).equals(user.getPasswordSet())
                && (StringUtils.hasText(user.getPhone()) || StringUtils.hasText(user.getEmail()));
    }

    private CurrentUserResponse buildResponse(UserEntity user) {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setGender(user.getGender());
        response.setAddress(user.getAddress());
        response.setBio(user.getBio());
        response.setStatus(user.getStatus());
        response.setPasswordSet(user.getPasswordSet());
        response.setProfileCompleted(user.getProfileCompleted());
        response.setAccountProtected(isAccountProtected(user));
        response.setProfileComplete(isProfileComplete(user));
        response.setCreateTime(user.getCreateTime());
        response.setLastLoginTime(user.getLastLoginTime());
        return response;
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
