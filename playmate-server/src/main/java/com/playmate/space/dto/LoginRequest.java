package com.playmate.space.dto;

import jakarta.validation.constraints.Size;

public class LoginRequest {

    @Size(max = 128, message = "长度不能超过 128")
    private String code;
    @Size(max = 128, message = "长度不能超过 128")
    private String mockOpenid;
    @Size(max = 64, message = "长度不能超过 64")
    private String nickname;
    @Size(max = 512, message = "长度不能超过 512")
    private String avatarUrl;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMockOpenid() {
        return mockOpenid;
    }

    public void setMockOpenid(String mockOpenid) {
        this.mockOpenid = mockOpenid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
