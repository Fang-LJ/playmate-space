package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.ForbiddenException;
import com.playmate.space.common.exception.NotFoundException;
import com.playmate.space.common.exception.UnauthorizedException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.mapper.ActivityMapper;
import com.playmate.space.mapper.ActivityMemberMapper;
import org.springframework.stereotype.Component;

@Component
public class ActivityCollaborationAccess {
    public static final String ROLE_CREATOR = "CREATOR";
    public static final String MEMBER_STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PLANNING = "PLANNING";
    public static final String STATUS_ONGOING = "ONGOING";

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper activityMemberMapper;

    public ActivityCollaborationAccess(ActivityMapper activityMapper, ActivityMemberMapper activityMemberMapper) {
        this.activityMapper = activityMapper;
        this.activityMemberMapper = activityMemberMapper;
    }

    public Long requireUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) throw new UnauthorizedException();
        return userId;
    }

    public ActivityEntity requireActivity(Long activityId) {
        if (activityId == null) throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "activityId 不能为空");
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) throw new NotFoundException("活动不存在");
        return activity;
    }

    public ActivityMemberEntity requireActiveMember(Long activityId, Long userId) {
        ActivityMemberEntity member = activityMemberMapper.selectOne(new LambdaQueryWrapper<ActivityMemberEntity>()
                .eq(ActivityMemberEntity::getActivityId, activityId)
                .eq(ActivityMemberEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null || !MEMBER_STATUS_ACTIVE.equals(member.getMemberStatus())) {
            throw new ForbiddenException("无权访问该活动");
        }
        return member;
    }

    public void requireWritableActivity(ActivityEntity activity) {
        if (!STATUS_PLANNING.equals(activity.getStatus()) && !STATUS_ONGOING.equals(activity.getStatus())) {
            throw new ForbiddenException("当前活动为只读状态");
        }
    }

    public boolean isActivityCreator(ActivityEntity activity, ActivityMemberEntity member, Long userId) {
        return ROLE_CREATOR.equals(member.getRole()) && userId.equals(activity.getCreatorUserId());
    }
}
