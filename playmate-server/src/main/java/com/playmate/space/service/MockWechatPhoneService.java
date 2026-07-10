package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Profile("local")
public class MockWechatPhoneService implements WechatPhoneService {

    private static final Map<String, String> PHONE_BY_CODE = Map.of(
            "mock_phone_a", "13800000001",
            "mock_phone_b", "13800000002",
            "mock_phone_c", "13800000003"
    );

    @Override
    public String resolvePhone(String code) {
        String phone = PHONE_BY_CODE.get(code == null ? "" : code.trim());
        if (phone == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "模拟手机号授权凭证无效");
        }
        return phone;
    }
}
