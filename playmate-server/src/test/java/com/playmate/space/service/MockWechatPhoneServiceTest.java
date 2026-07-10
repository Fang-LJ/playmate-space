package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockWechatPhoneServiceTest {

    private final MockWechatPhoneService service = new MockWechatPhoneService();

    @Test
    void shouldResolveFixedMockPhones() {
        assertEquals("13800000001", service.resolvePhone("mock_phone_a"));
        assertEquals("13800000002", service.resolvePhone("mock_phone_b"));
        assertEquals("13800000003", service.resolvePhone("mock_phone_c"));
    }

    @Test
    void shouldRejectUnknownMockPhoneCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.resolvePhone("unknown_code")
        );
        assertEquals("模拟手机号授权凭证无效", exception.getMessage());
    }
}
