package com.playmate.space.dto.user;

import jakarta.validation.constraints.Size;

public class UpdateUserProfileRequest {

    @Size(max = 64)
    private String nickname;

    @Size(max = 512)
    private String avatarUrl;

    @Size(max = 32)
    private String phone;

    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String gender;

    @Size(max = 255)
    private String address;

    @Size(max = 512)
    private String bio;

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

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
