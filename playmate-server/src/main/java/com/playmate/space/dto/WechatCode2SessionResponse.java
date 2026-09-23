package com.playmate.space.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WechatCode2SessionResponse(
        String openid,
        String unionid,
        @JsonProperty("errcode") Integer errorCode
) {
}
