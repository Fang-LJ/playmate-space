package com.playmate.space.dto;

public class LoginRequest {

    private String code;
    private String mockOpenid;
    private String nickname;
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
