package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PasswordService {

    private static final int MIN_LENGTH = 6;
    private static final int MAX_LENGTH = 64;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hashPassword(String rawPassword) {
        validateRawPassword(rawPassword);
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(passwordHash)) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }

    private void validateRawPassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "密码不能为空");
        }
        int length = rawPassword.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "密码长度需为 6-64 位");
        }
    }
}
