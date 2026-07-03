package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.LoginRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local")
public class DefaultWechatLoginService implements WechatLoginService {

    @Override
    public WechatSession resolveSession(LoginRequest request) {
        // TODO: Call WeChat code2Session with AppID/AppSecret from secure configuration.
        throw new BusinessException("真实微信登录尚未配置");
    }
}
