package com.playmate.space.dto.user;

import jakarta.validation.constraints.Size;

public class UpdateMyAccountRequest {

    @Size(max = 32, message = "手机号长度不能超过 32")
    private String phone;

    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
    private String password;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
