package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.common.exception.UnauthorizedException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.dto.member.ActivityMemberResponse;
import com.playmate.space.dto.member.UpdateMyNicknameRequest;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.ActivityMapper;
import com.playmate.space.mapper.ActivityMemberMapper;
import com.playmate.space.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityMemberService {

    private static final String ROLE_CREATOR = "CREATOR";
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String MEMBER_STATUS_ACTIVE = "ACTIVE";
    private static final String MEMBER_STATUS_REMOVED = "REMOVED";

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper activityMemberMapper;
    private final UserMapper userMapper;

    public ActivityMemberService(
            ActivityMapper activityMapper,
            ActivityMemberMapper activityMemberMapper,
            UserMapper userMapper
    ) {
        this.activityMapper = activityMapper;
        this.activityMemberMapper = activityMemberMapper;
        this.userMapper = userMapper;
    }

    public List<ActivityMemberResponse> listMembers(Long activityId) {
        Long userId = requireLoginUserId();
        getExistingActivity(activityId);
        requireActiveMember(activityId, userId);

        List<ActivityMemberEntity> members = activityMemberMapper.selectList(new LambdaQueryWrapper<ActivityMemberEntity>()
                .eq(ActivityMemberEntity::getActivityId, activityId)
                .eq(ActivityMemberEntity::getMemberStatus, MEMBER_STATUS_ACTIVE));
        members.sort(Comparator
                .comparing((ActivityMemberEntity member) -> ROLE_CREATOR.equals(member.getRole()) ? 0 : 1)
                .thenComparing(ActivityMemberEntity::getJoinTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ActivityMemberEntity::getId));

        Map<Long, UserEntity> userMap = loadUserMap(members);
        List<ActivityMemberResponse> responses = new ArrayList<>();
        for (ActivityMemberEntity member : members) {
            responses.add(buildMemberResponse(member, userMap.get(member.getUserId()), userId));
        }
        return responses;
    }

    @Transactional
    public ActivityMemberResponse updateMyNickname(Long activityId, UpdateMyNicknameRequest request) {
        Long userId = requireLoginUserId();
        getExistingActivity(activityId);
        ActivityMemberEntity member = requireActiveMember(activityId, userId);

        String nickname = request.getNickname().trim();
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "昵称不能为空");
        }

        member.setActivityNickname(nickname);
        member.setUpdateTime(LocalDateTime.now());
        activityMemberMapper.updateById(member);

        UserEntity user = userMapper.selectById(userId);
        return buildMemberResponse(member, user, userId);
    }

    @Transactional
    public void removeMember(Long activityId, Long memberId) {
        Long userId = requireLoginUserId();
        ActivityEntity activity = getExistingActivity(activityId);
        ActivityMemberEntity operator = requireActiveMember(activityId, userId);
        if (!ROLE_CREATOR.equals(operator.getRole()) || !userId.equals(activity.getCreatorUserId())) {
            throw new ForbiddenException("只有活动创建者可以移除成员");
        }

        ActivityMemberEntity target = activityMemberMapper.selectById(memberId);
        if (target == null || !activityId.equals(target.getActivityId())) {
            throw new NotFoundException("成员不存在");
        }
        if (ROLE_CREATOR.equals(target.getRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "不能移除创建者");
        }
        if (!ROLE_MEMBER.equals(target.getRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "只能移除普通成员");
        }
        if (userId.equals(target.getUserId()) || operator.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "不能移除自己");
        }
        if (MEMBER_STATUS_REMOVED.equals(target.getMemberStatus())) {
            return;
        }
        if (!MEMBER_STATUS_ACTIVE.equals(target.getMemberStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "当前成员状态不可移除");
        }

        LocalDateTime now = LocalDateTime.now();
        target.setMemberStatus(MEMBER_STATUS_REMOVED);
        target.setRemovedTime(now);
        target.setRemovedBy(userId);
        target.setUpdateTime(now);
        activityMemberMapper.updateById(target);

        int updated = activityMapper.decrementMemberCount(activityId, now);
        if (updated != 1) {
            throw new BusinessException("移除成员失败，请重试");
        }
    }

    private Long requireLoginUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return userId;
    }

    private ActivityEntity getExistingActivity(Long activityId) {
        if (activityId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "activityId 不能为空");
        }
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new NotFoundException("活动不存在");
        }
        return activity;
    }

    private ActivityMemberEntity requireActiveMember(Long activityId, Long userId) {
        ActivityMemberEntity member = activityMemberMapper.selectOne(new LambdaQueryWrapper<ActivityMemberEntity>()
                .eq(ActivityMemberEntity::getActivityId, activityId)
                .eq(ActivityMemberEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null || !MEMBER_STATUS_ACTIVE.equals(member.getMemberStatus())) {
            throw new ForbiddenException("无权访问该活动");
        }
        return member;
    }

    private Map<Long, UserEntity> loadUserMap(List<ActivityMemberEntity> members) {
        List<Long> userIds = members.stream()
                .map(ActivityMemberEntity::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .in(UserEntity::getId, userIds))
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (left, right) -> left));
    }

    private ActivityMemberResponse buildMemberResponse(
            ActivityMemberEntity member,
            UserEntity user,
            Long currentUserId
    ) {
        ActivityMemberResponse response = new ActivityMemberResponse();
        response.setMemberId(member.getId());
        response.setUserId(member.getUserId());
        response.setNickname(resolveNickname(member, user));
        response.setAvatarUrl(user == null ? null : user.getAvatarUrl());
        response.setRole(member.getRole());
        response.setMemberStatus(member.getMemberStatus());
        response.setJoinedTime(member.getJoinTime());
        response.setIsCurrentUser(member.getUserId() != null && member.getUserId().equals(currentUserId));
        return response;
    }

    private String resolveNickname(ActivityMemberEntity member, UserEntity user) {
        if (StringUtils.hasText(member.getActivityNickname())) {
            return member.getActivityNickname();
        }
        if (user != null && StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return "玩伴用户";
    }
}
