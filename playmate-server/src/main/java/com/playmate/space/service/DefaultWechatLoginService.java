package com.playmate.space.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.LoginRequest;
import com.playmate.space.dto.WechatCode2SessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.time.Duration;

@Service
@Profile("!local")
public class DefaultWechatLoginService implements WechatLoginService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWechatLoginService.class);
    private static final String WECHAT_API_BASE_URL = "https://api.weixin.qq.com";
    private final WechatProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public DefaultWechatLoginService(WechatProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, WECHAT_API_BASE_URL);
    }

    DefaultWechatLoginService(WechatProperties properties, ObjectMapper objectMapper, String apiBaseUrl) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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

        byte[] responseBody;
        try {
            responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/sns/jscode2session")
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .queryParam("js_code", request.getCode().trim())
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException exception) {
            // Never log the exception object or message: they may contain the URL and AppSecret.
            String causeType = exception.getCause() == null ? "none" : exception.getCause().getClass().getName();
            String httpStatus = exception instanceof RestClientResponseException responseException
                    ? String.valueOf(responseException.getStatusCode().value()) : "none";
            log.warn("WeChat code2Session HTTP failure: type={}, causeType={}, status={}",
                    exception.getClass().getName(), causeType, httpStatus);
            throw new BusinessException("微信登录服务暂不可用，请稍后重试");
        }

        if (responseBody == null || responseBody.length == 0) {
            throw new BusinessException("微信登录服务返回空响应");
        }
        WechatCode2SessionResponse response;
        try {
            response = objectMapper.readValue(responseBody, WechatCode2SessionResponse.class);
        } catch (IOException exception) {
            // Jackson exception messages can contain response excerpts; log only the type.
            log.warn("WeChat code2Session parse failure: type={}", exception.getClass().getName());
            throw new BusinessException("微信登录响应格式错误");
        }
        if (response.errorCode() != null && response.errorCode() != 0) {
            log.warn("WeChat code2Session rejected login: errcode={}", response.errorCode());
            throw new BusinessException("微信登录失败，错误码：" + response.errorCode());
        }
        if (!StringUtils.hasText(response.openid())) {
            throw new BusinessException("微信登录未返回 openid");
        }
        return new WechatSession(response.openid(), response.unionid());
    }
}
