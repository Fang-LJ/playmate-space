package com.playmate.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AccountLoginRequest {

    @NotBlank(message = "账号不能为空")
    @Size(max = 128, message = "账号长度不能超过 128")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
    private String password;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
