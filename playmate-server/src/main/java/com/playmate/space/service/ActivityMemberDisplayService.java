package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.ActivityMemberMapper;
import com.playmate.space.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActivityMemberDisplayService {
    private static final String FALLBACK_NAME = "玩伴用户";

    private final ActivityMemberMapper memberMapper;
    private final UserMapper userMapper;

    public ActivityMemberDisplayService(ActivityMemberMapper memberMapper, UserMapper userMapper) {
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
    }

    public Map<Long, ParticipantProfile> loadParticipantProfiles(Long activityId, Collection<Long> userIds) {
        Set<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();

        Map<Long, ActivityMemberEntity> members = memberMapper.selectList(
                        new LambdaQueryWrapper<ActivityMemberEntity>()
                                .eq(ActivityMemberEntity::getActivityId, activityId)
                                .in(ActivityMemberEntity::getUserId, ids))
                .stream()
                .collect(Collectors.toMap(ActivityMemberEntity::getUserId, Function.identity(), (first, second) -> first));
        Map<Long, UserEntity> users = userMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (first, second) -> first));

        Map<Long, ParticipantProfile> result = new HashMap<>();
        for (Long userId : ids) {
            ActivityMemberEntity member = members.get(userId);
            UserEntity user = users.get(userId);
            String displayName = member != null && StringUtils.hasText(member.getActivityNickname())
                    ? member.getActivityNickname().trim()
                    : user != null && StringUtils.hasText(user.getNickname()) ? user.getNickname().trim() : FALLBACK_NAME;
            result.put(userId, new ParticipantProfile(userId, displayName, user == null ? null : user.getAvatarUrl()));
        }
        return result;
    }
}
