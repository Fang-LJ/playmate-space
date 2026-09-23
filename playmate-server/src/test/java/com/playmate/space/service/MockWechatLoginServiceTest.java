package com.playmate.space.service;

import com.playmate.space.dto.LoginRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockWechatLoginServiceTest {

    private final MockWechatLoginService service = new MockWechatLoginService();

    @Test
    void keepsLocalMockUsersAvailable() {
        for (String user : new String[]{"a", "b", "c"}) {
            LoginRequest request = new LoginRequest();
            request.setMockOpenid("mock_user_" + user);
            assertEquals("mock_user_" + user, service.resolveSession(request).openid());
        }
    }
}
