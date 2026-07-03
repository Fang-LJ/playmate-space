package com.playmate.space.service;

import com.playmate.space.dto.LoginRequest;

public interface WechatLoginService {

    WechatSession resolveSession(LoginRequest request);

    record WechatSession(String openid, String unionid) {
    }
}
