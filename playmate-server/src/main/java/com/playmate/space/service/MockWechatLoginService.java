package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.LoginRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local")
public class MockWechatLoginService implements WechatLoginService {

    @Override
    public WechatSession resolveSession(LoginRequest request) {
        String openid = resolveOpenid(request);
        return new WechatSession(openid, null);
    }

    private String resolveOpenid(LoginRequest request) {
        if (request != null && StringUtils.hasText(request.getMockOpenid())) {
            return request.getMockOpenid().trim();
        }
        if (request != null && StringUtils.hasText(request.getCode())) {
            return "mock_" + request.getCode().trim();
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "code 或 mockOpenid 不能为空");
    }
}
