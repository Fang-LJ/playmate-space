package com.playmate.space.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WechatPhoneBindRequest {

    @NotBlank(message = "手机号授权凭证不能为空")
    @Size(max = 128, message = "手机号授权凭证长度不能超过 128")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
