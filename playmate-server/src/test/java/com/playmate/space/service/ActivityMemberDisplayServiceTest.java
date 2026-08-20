package com.playmate.space.service;

import com.playmate.space.entity.ActivityMemberEntity;
import com.playmate.space.entity.UserEntity;
import com.playmate.space.mapper.ActivityMemberMapper;
import com.playmate.space.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityMemberDisplayServiceTest {
    @Test
    void activityNicknameWinsAndIsReadFreshOnEveryRequest() {
        ActivityMemberMapper memberMapper = mock(ActivityMemberMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        ActivityMemberDisplayService service = new ActivityMemberDisplayService(memberMapper, userMapper);
        ActivityMemberEntity oldMember = member("旧活动昵称");
        ActivityMemberEntity newMember = member("新活动昵称");
        when(memberMapper.selectList(any())).thenReturn(List.of(oldMember), List.of(newMember));
        when(userMapper.selectByIds(any())).thenReturn(List.of(user("微信用户A")));

        String first = service.loadParticipantProfiles(10L, Set.of(1L)).get(1L).displayName();
        String second = service.loadParticipantProfiles(10L, Set.of(1L)).get(1L).displayName();

        assertEquals("旧活动昵称", first);
        assertEquals("新活动昵称", second);
    }

    private ActivityMemberEntity member(String nickname) {
        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setActivityId(10L); member.setUserId(1L); member.setActivityNickname(nickname);
        return member;
    }

    private UserEntity user(String nickname) {
        UserEntity user = new UserEntity();
        user.setId(1L); user.setNickname(nickname); user.setAvatarUrl("avatar");
        return user;
    }
}
