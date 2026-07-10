package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.security.JwtUtils;
import com.playmate.space.dto.AccountLoginRequest;
import com.playmate.space.dto.AccountRegisterRequest;
import com.playmate.space.dto.LoginRequest;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.entity.UserIdentityEntity;
import com.playmate.space.mapper.UserIdentityMapper;
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
    private static final String IDENTITY_WECHAT_MINIPROGRAM = "WECHAT_MINIPROGRAM";
    private static final String LOGIN_TYPE_WECHAT = "WECHAT_MINIPROGRAM";
    private static final String LOGIN_TYPE_ACCOUNT = "ACCOUNT";

    private final UserMapper userMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final JwtUtils jwtUtils;
    private final WechatLoginService wechatLoginService;
    private final PasswordService passwordService;

    public AuthService(
            UserMapper userMapper,
            UserIdentityMapper userIdentityMapper,
            JwtUtils jwtUtils,
            WechatLoginService wechatLoginService,
            PasswordService passwordService
    ) {
        this.userMapper = userMapper;
        this.userIdentityMapper = userIdentityMapper;
        this.jwtUtils = jwtUtils;
        this.wechatLoginService = wechatLoginService;
        this.passwordService = passwordService;
    }

    @Transactional
    public LoginResponse wxLogin(LoginRequest request) {
        WechatLoginService.WechatSession session = wechatLoginService.resolveSession(request);
        if (session == null || !StringUtils.hasText(session.openid())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "openid 获取失败");
        }

        LocalDateTime now = LocalDateTime.now();
        UserIdentityEntity identity = findWechatIdentity(session.openid());
        UserEntity user;
        boolean isNewUser;
        if (identity == null) {
            user = createWechatUser(request, session, now);
            createWechatIdentity(user, request, session, now);
            isNewUser = true;
        } else {
            user = userMapper.selectById(identity.getUserId());
            if (user == null || Integer.valueOf(1).equals(user.getDeleteFlag())) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED.code(), "用户不存在或登录已失效");
            }
            ensureUserEnabled(user);
            updateWechatLoginInfo(user, identity, request, now);
            isNewUser = false;
        }
        return buildLoginResponse(user, LOGIN_TYPE_WECHAT, isNewUser);
    }

    @Transactional
    public LoginResponse accountRegister(AccountRegisterRequest request) {
        String account = normalizeRequired(request.getAccount(), "账号不能为空");
        String nickname = normalizeNullableText(request.getNickname());
        boolean emailAccount = isEmailAccount(account);
        assertAccountNotUsed(account, emailAccount, null);

        LocalDateTime now = LocalDateTime.now();
        UserEntity user = new UserEntity();
        user.setNickname(StringUtils.hasText(nickname) ? nickname : "玩伴用户");
        user.setStatus(USER_STATUS_NORMAL);
        user.setPasswordHash(passwordService.hashPassword(request.getPassword()));
        user.setPasswordSet(1);
        user.setGender("UNKNOWN");
        if (emailAccount) {
            user.setEmail(account);
        } else {
            user.setPhone(account);
        }
        user.setProfileCompleted(resolveProfileCompleted(user));
        user.setLastLoginTime(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setDeleteFlag(0);
        userMapper.insert(user);
        return buildLoginResponse(user, LOGIN_TYPE_ACCOUNT, true);
    }

    @Transactional
    public LoginResponse accountLogin(AccountLoginRequest request) {
        String account = normalizeRequired(request.getAccount(), "账号不能为空");
        UserEntity user = findByAccount(account);
        if (user == null) {
            throw invalidAccountOrPassword();
        }
        ensureUserEnabled(user);
        if (!Integer.valueOf(1).equals(user.getPasswordSet()) || !StringUtils.hasText(user.getPasswordHash())) {
            throw invalidAccountOrPassword();
        }
        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            throw invalidAccountOrPassword();
        }
        LocalDateTime now = LocalDateTime.now();
        UserEntity updateEntity = new UserEntity();
        updateEntity.setId(user.getId());
        updateEntity.setLastLoginTime(now);
        updateEntity.setUpdateTime(now);
        userMapper.updateById(updateEntity);
        user.setLastLoginTime(now);
        return buildLoginResponse(user, LOGIN_TYPE_ACCOUNT, false);
    }

    private UserIdentityEntity findWechatIdentity(String openid) {
        return userIdentityMapper.selectOne(new LambdaQueryWrapper<UserIdentityEntity>()
                .eq(UserIdentityEntity::getIdentityType, IDENTITY_WECHAT_MINIPROGRAM)
                .eq(UserIdentityEntity::getIdentifier, openid)
                .eq(UserIdentityEntity::getDeleteFlag, 0)
                .last("LIMIT 1"));
    }

    private UserEntity createWechatUser(
            LoginRequest request,
            WechatLoginService.WechatSession session,
            LocalDateTime now
    ) {
        UserEntity user = new UserEntity();
        // openid/unionid remain nullable legacy columns. New identity binding lives in t_user_identity.
        user.setNickname(resolveNullableText(request == null ? null : request.getNickname()));
        if (!StringUtils.hasText(user.getNickname())) {
            user.setNickname("玩伴用户");
        }
        user.setAvatarUrl(resolveNullableText(request == null ? null : request.getAvatarUrl()));
        user.setStatus(USER_STATUS_NORMAL);
        user.setPasswordSet(0);
        user.setGender("UNKNOWN");
        user.setProfileCompleted(resolveProfileCompleted(user));
        user.setLastLoginTime(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setDeleteFlag(0);
        userMapper.insert(user);
        return user;
    }

    private void createWechatIdentity(
            UserEntity user,
            LoginRequest request,
            WechatLoginService.WechatSession session,
            LocalDateTime now
    ) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setUserId(user.getId());
        identity.setIdentityType(IDENTITY_WECHAT_MINIPROGRAM);
        identity.setIdentifier(session.openid());
        identity.setUnionid(session.unionid());
        identity.setAuthNickname(resolveNullableText(request == null ? null : request.getNickname()));
        identity.setAuthAvatarUrl(resolveNullableText(request == null ? null : request.getAvatarUrl()));
        identity.setLastLoginTime(now);
        identity.setBindTime(now);
        identity.setCreateTime(now);
        identity.setUpdateTime(now);
        identity.setDeleteFlag(0);
        userIdentityMapper.insert(identity);
    }

    private void updateWechatLoginInfo(
            UserEntity user,
            UserIdentityEntity identity,
            LoginRequest request,
            LocalDateTime now
    ) {
        UserEntity updateUser = new UserEntity();
        updateUser.setId(user.getId());
        boolean userChanged = false;
        if (!StringUtils.hasText(user.getNickname()) && StringUtils.hasText(request == null ? null : request.getNickname())) {
            updateUser.setNickname(request.getNickname().trim());
            user.setNickname(updateUser.getNickname());
            userChanged = true;
        }
        if (!StringUtils.hasText(user.getAvatarUrl()) && StringUtils.hasText(request == null ? null : request.getAvatarUrl())) {
            updateUser.setAvatarUrl(request.getAvatarUrl().trim());
            user.setAvatarUrl(updateUser.getAvatarUrl());
            userChanged = true;
        }
        updateUser.setLastLoginTime(now);
        updateUser.setProfileCompleted(resolveProfileCompleted(user));
        updateUser.setUpdateTime(now);
        user.setLastLoginTime(now);
        user.setProfileCompleted(updateUser.getProfileCompleted());
        userChanged = true;
        if (userChanged) {
            userMapper.updateById(updateUser);
        }

        UserIdentityEntity updateIdentity = new UserIdentityEntity();
        updateIdentity.setId(identity.getId());
        updateIdentity.setLastLoginTime(now);
        String authNickname = resolveNullableText(request == null ? null : request.getNickname());
        String authAvatarUrl = resolveNullableText(request == null ? null : request.getAvatarUrl());
        if (StringUtils.hasText(authNickname)) {
            updateIdentity.setAuthNickname(authNickname);
        }
        if (StringUtils.hasText(authAvatarUrl)) {
            updateIdentity.setAuthAvatarUrl(authAvatarUrl);
        }
        updateIdentity.setUpdateTime(now);
        userIdentityMapper.updateById(updateIdentity);
    }

    private UserEntity findByAccount(String account) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeleteFlag, 0)
                .last("LIMIT 1");
        if (isEmailAccount(account)) {
            wrapper.eq(UserEntity::getEmail, account);
        } else {
            wrapper.eq(UserEntity::getPhone, account);
        }
        return userMapper.selectOne(wrapper);
    }

    private void assertAccountNotUsed(String account, boolean emailAccount, Long currentUserId) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeleteFlag, 0)
                .last("LIMIT 1");
        if (emailAccount) {
            wrapper.eq(UserEntity::getEmail, account);
        } else {
            wrapper.eq(UserEntity::getPhone, account);
        }
        UserEntity existing = userMapper.selectOne(wrapper);
        if (existing != null && (currentUserId == null || !existing.getId().equals(currentUserId))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), emailAccount ? "邮箱已被使用" : "手机号已被使用");
        }
    }

    private void ensureUserEnabled(UserEntity user) {
        if (USER_STATUS_DISABLED.equals(user.getStatus())) {
            throw new ForbiddenException("用户已被禁用");
        }
    }

    private LoginResponse buildLoginResponse(UserEntity user, String loginType, boolean isNewUser) {
        boolean accountProtected = isAccountProtected(user);
        boolean profileComplete = isProfileComplete(user);
        LoginResponse response = new LoginResponse();
        response.setToken(jwtUtils.generateToken(user.getId()));
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setIsNewUser(isNewUser);
        response.setAccountProtected(accountProtected);
        response.setProfileComplete(profileComplete);
        response.setShowAccountProtectionNotice(!accountProtected);
        // Compatibility only. Clients must use accountProtected/profileComplete for display, never routing.
        response.setNeedSetPassword(!Integer.valueOf(1).equals(user.getPasswordSet()));
        response.setNeedCompleteProfile(!profileComplete);
        response.setLoginType(loginType);
        return response;
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

    private boolean isEmailAccount(String account) {
        return account != null && account.contains("@");
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), message);
        }
        return value.trim();
    }

    private String resolveNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        return resolveNullableText(value);
    }

    private BusinessException invalidAccountOrPassword() {
        return new BusinessException(ErrorCode.PARAM_ERROR.code(), "账号或密码错误");
    }
}
