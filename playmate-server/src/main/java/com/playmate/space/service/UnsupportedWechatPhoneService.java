package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local")
public class UnsupportedWechatPhoneService implements WechatPhoneService {

    @Override
    public String resolvePhone(String code) {
        throw new BusinessException("真实微信手机号授权尚未接入");
    }
}
