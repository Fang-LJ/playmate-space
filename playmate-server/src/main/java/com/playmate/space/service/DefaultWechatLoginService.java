package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.LoginRequest;
import com.playmate.space.dto.WechatCode2SessionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Service
@Profile("!local")
public class DefaultWechatLoginService implements WechatLoginService {

    private static final String WECHAT_API_BASE_URL = "https://api.weixin.qq.com";
    private final WechatProperties properties;
    private final RestClient restClient;

    @Autowired
    public DefaultWechatLoginService(WechatProperties properties) {
        this(properties, WECHAT_API_BASE_URL);
    }

    DefaultWechatLoginService(WechatProperties properties, String apiBaseUrl) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public WechatSession resolveSession(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "微信登录 code 不能为空");
        }
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            throw new BusinessException("微信登录服务未配置");
        }

        WechatCode2SessionResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/sns/jscode2session")
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .queryParam("js_code", request.getCode().trim())
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(WechatCode2SessionResponse.class);
        } catch (RestClientException exception) {
            // HTTP client exceptions can include the request URL, which contains AppSecret.
            throw new BusinessException("微信登录服务暂不可用，请稍后重试");
        }

        if (response == null) {
            throw new BusinessException("微信登录服务返回空响应");
        }
        if (response.errorCode() != null && response.errorCode() != 0) {
            throw new BusinessException("微信登录失败，错误码：" + response.errorCode());
        }
        if (!StringUtils.hasText(response.openid())) {
            throw new BusinessException("微信登录未返回 openid");
        }
        return new WechatSession(response.openid(), response.unionid());
    }
}
