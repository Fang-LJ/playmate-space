package com.playmate.space.vo;

public class LoginResponse {

    private String token;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Boolean needCompleteProfile;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public Boolean getNeedCompleteProfile() {
        return needCompleteProfile;
    }

    public void setNeedCompleteProfile(Boolean needCompleteProfile) {
        this.needCompleteProfile = needCompleteProfile;
    }
}
