package com.playmate.space.dto.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateMyNicknameRequest {

    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "不能超过 64 个字符")
    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
