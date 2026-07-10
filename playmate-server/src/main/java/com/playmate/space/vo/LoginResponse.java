package com.playmate.space.vo;

public class LoginResponse {

    private String token;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Boolean isNewUser;
    private Boolean accountProtected;
    private Boolean profileComplete;
    private Boolean showAccountProtectionNotice;
    // Kept for older clients during the transition. New clients must not use these as redirect signals.
    private Boolean needCompleteProfile;
    private Boolean needSetPassword;
    private String loginType;
    private String phone;
    private String email;

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

    public Boolean getIsNewUser() {
        return isNewUser;
    }

    public void setIsNewUser(Boolean isNewUser) {
        this.isNewUser = isNewUser;
    }

    public Boolean getAccountProtected() {
        return accountProtected;
    }

    public void setAccountProtected(Boolean accountProtected) {
        this.accountProtected = accountProtected;
    }

    public Boolean getProfileComplete() {
        return profileComplete;
    }

    public void setProfileComplete(Boolean profileComplete) {
        this.profileComplete = profileComplete;
    }

    public Boolean getShowAccountProtectionNotice() {
        return showAccountProtectionNotice;
    }

    public void setShowAccountProtectionNotice(Boolean showAccountProtectionNotice) {
        this.showAccountProtectionNotice = showAccountProtectionNotice;
    }

    public Boolean getNeedCompleteProfile() {
        return needCompleteProfile;
    }

    public void setNeedCompleteProfile(Boolean needCompleteProfile) {
        this.needCompleteProfile = needCompleteProfile;
    }

    public Boolean getNeedSetPassword() {
        return needSetPassword;
    }

    public void setNeedSetPassword(Boolean needSetPassword) {
        this.needSetPassword = needSetPassword;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
