package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.common.exception.UnauthorizedException;
import com.playmate.space.common.security.JwtUtils;
import com.playmate.space.dto.activity.ActivityInviteInfoResponse;
import com.playmate.space.dto.activity.JoinActivityResponse;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.ActivityMapper;
import com.playmate.space.mapper.ActivityMemberMapper;
import com.playmate.space.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ActivityInviteService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String STATUS_PLANNING = "PLANNING";
    private static final String STATUS_ENDED = "ENDED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String MEMBER_STATUS_ACTIVE = "ACTIVE";
    private static final String MEMBER_STATUS_REMOVED = "REMOVED";
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String JOIN_SOURCE_SHARE = "SHARE";
    private static final String USER_STATUS_NORMAL = "NORMAL";
    private static final int INVITE_DESCRIPTION_MAX_LENGTH = 200;

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper activityMemberMapper;
    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;

    public ActivityInviteService(
            ActivityMapper activityMapper,
            ActivityMemberMapper activityMemberMapper,
            UserMapper userMapper,
            JwtUtils jwtUtils
    ) {
        this.activityMapper = activityMapper;
        this.activityMemberMapper = activityMemberMapper;
        this.userMapper = userMapper;
        this.jwtUtils = jwtUtils;
    }

    public ActivityInviteInfoResponse getInviteInfo(String shareCode, HttpServletRequest request) {
        ActivityEntity activity = getActivityByShareCode(shareCode);
        Long optionalUserId = resolveOptionalUserId(request);
        ActivityMemberEntity member = optionalUserId == null ? null : findMember(activity.getId(), optionalUserId);
        UserEntity creator = userMapper.selectById(activity.getCreatorUserId());

        ActivityInviteInfoResponse response = new ActivityInviteInfoResponse();
        response.setActivityId(activity.getId());
        response.setShareCode(activity.getShareCode());
        response.setName(activity.getActivityName());
        response.setType(activity.getActivityType());
        response.setStatus(activity.getStatus());
        response.setCoverUrl(activity.getCoverUrl());
        response.setLocationName(activity.getLocationName());
        response.setStartDate(activity.getStartDate());
        response.setEndDate(activity.getEndDate());
        response.setDescription(limitDescription(activity.getDescription()));
        response.setCreatorNickname(creator == null ? null : creator.getNickname());
        response.setMemberCount(activity.getMemberCount());
        applyJoinState(response, activity, member);
        return response;
    }

    @Transactional
    public JoinActivityResponse joinActivity(String shareCode, HttpServletRequest request) {
        Long userId = requireLoginUserId(request);
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在或登录已失效");
        }
        if (!USER_STATUS_NORMAL.equals(user.getStatus())) {
            throw new ForbiddenException("用户已被禁用");
        }

        ActivityEntity activity = getActivityByShareCode(shareCode);
        validateActivityCanJoin(activity);

        ActivityMemberEntity existingMember = findMember(activity.getId(), userId);
        if (existingMember != null) {
            if (MEMBER_STATUS_ACTIVE.equals(existingMember.getMemberStatus())) {
                return buildJoinResponse(activity.getId(), existingMember, "已加入");
            }
            if (MEMBER_STATUS_REMOVED.equals(existingMember.getMemberStatus())) {
                throw new ForbiddenException("你已被移除，无法重新加入");
            }
            throw new ForbiddenException("当前成员状态不允许加入");
        }

        LocalDateTime now = LocalDateTime.now();
        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setActivityId(activity.getId());
        member.setUserId(userId);
        member.setRole(ROLE_MEMBER);
        member.setMemberStatus(MEMBER_STATUS_ACTIVE);
        member.setActivityNickname(null);
        member.setJoinSource(JOIN_SOURCE_SHARE);
        member.setJoinTime(now);
        member.setCreateTime(now);
        member.setUpdateTime(now);
        member.setDeleteFlag(0);
        activityMemberMapper.insert(member);

        int updated = activityMapper.incrementMemberCount(activity.getId(), now);
        if (updated != 1) {
            throw new BusinessException("加入活动失败，请重试");
        }

        return buildJoinResponse(activity.getId(), member, "加入成功");
    }

    private void applyJoinState(
            ActivityInviteInfoResponse response,
            ActivityEntity activity,
            ActivityMemberEntity member
    ) {
        response.setJoined(false);
        if (STATUS_CANCELED.equals(activity.getStatus())) {
            response.setCanJoin(false);
            response.setReason("活动已取消");
            return;
        }
        if (STATUS_ENDED.equals(activity.getStatus())) {
            response.setCanJoin(false);
            response.setReason("活动已结束，第一版不可加入");
            return;
        }
        if (!STATUS_PLANNING.equals(activity.getStatus())) {
            response.setCanJoin(false);
            response.setReason("当前活动状态不可加入");
            return;
        }
        if (member == null) {
            response.setCanJoin(true);
            response.setReason(null);
            return;
        }
        if (MEMBER_STATUS_ACTIVE.equals(member.getMemberStatus())) {
            response.setJoined(true);
            response.setCanJoin(false);
            response.setReason("已加入");
            return;
        }
        if (MEMBER_STATUS_REMOVED.equals(member.getMemberStatus())) {
            response.setCanJoin(false);
            response.setReason("你已被移除，无法重新加入");
            return;
        }
        response.setCanJoin(false);
        response.setReason("当前成员状态不可加入");
    }

    private void validateActivityCanJoin(ActivityEntity activity) {
        if (STATUS_CANCELED.equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "活动已取消，无法加入");
        }
        if (STATUS_ENDED.equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "活动已结束，第一版不可加入");
        }
        if (!STATUS_PLANNING.equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "当前活动状态不可加入");
        }
    }

    private ActivityEntity getActivityByShareCode(String shareCode) {
        if (!StringUtils.hasText(shareCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "shareCode 不能为空");
        }
        ActivityEntity activity = activityMapper.selectOne(new LambdaQueryWrapper<ActivityEntity>()
                .eq(ActivityEntity::getShareCode, shareCode.trim())
                .last("LIMIT 1"));
        if (activity == null) {
            throw new NotFoundException("链接失效或活动不存在");
        }
        return activity;
    }

    private ActivityMemberEntity findMember(Long activityId, Long userId) {
        return activityMemberMapper.selectOne(new LambdaQueryWrapper<ActivityMemberEntity>()
                .eq(ActivityMemberEntity::getActivityId, activityId)
                .eq(ActivityMemberEntity::getUserId, userId)
                .last("LIMIT 1"));
    }

    private Long resolveOptionalUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            return jwtUtils.parseUserId(token);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Long requireLoginUserId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new UnauthorizedException();
        }
        try {
            return jwtUtils.parseUserId(token);
        } catch (RuntimeException exception) {
            throw new UnauthorizedException();
        }
    }

    private String limitDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() <= INVITE_DESCRIPTION_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, INVITE_DESCRIPTION_MAX_LENGTH);
    }

    private JoinActivityResponse buildJoinResponse(Long activityId, ActivityMemberEntity member, String message) {
        JoinActivityResponse response = new JoinActivityResponse();
        response.setActivityId(activityId);
        response.setMemberId(member.getId());
        response.setRole(member.getRole());
        response.setMemberStatus(member.getMemberStatus());
        response.setJoined(true);
        response.setMessage(message);
        return response;
    }
}
