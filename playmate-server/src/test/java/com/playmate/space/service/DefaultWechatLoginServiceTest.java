package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.LoginRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWechatLoginServiceTest {

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private String responseBody;
    private String requestQuery;
    private DefaultWechatLoginService service;

    @BeforeEach
    void setUp() throws IOException {
        WechatProperties properties = new WechatProperties();
        properties.setAppId("test-app-id");
        properties.setAppSecret("test-secret");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sns/jscode2session", exchange -> {
            requestCount.incrementAndGet();
            requestQuery = exchange.getRequestURI().getRawQuery();
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        service = new DefaultWechatLoginService(properties, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void rejectsBlankCodeBeforeCallingWechat() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.resolveSession(loginRequest(" ")));
        assertEquals(ErrorCode.PARAM_ERROR.code(), exception.getCode());
        assertEquals(0, requestCount.get());
        assertThrows(BusinessException.class, () -> service.resolveSession(null));
    }

    @Test
    void exchangesCodeForOpenidAndUnionid() {
        responseBody = "{\"openid\":\"openid-123\",\"unionid\":\"unionid-456\",\"session_key\":\"private-key\"}";

        WechatLoginService.WechatSession session = service.resolveSession(loginRequest(" code-123 "));

        assertEquals("openid-123", session.openid());
        assertEquals("unionid-456", session.unionid());
        assertEquals(1, requestCount.get());
        Map<String, String> query = Arrays.stream(requestQuery.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(part -> decode(part[0]), part -> decode(part[1])));
        assertEquals("test-app-id", query.get("appid"));
        assertEquals("test-secret", query.get("secret"));
        assertEquals("code-123", query.get("js_code"));
        assertEquals("authorization_code", query.get("grant_type"));
        assertFalse(session.toString().contains("private-key"));
    }

    @Test
    void convertsWechatErrorWithoutExposingItsResponse() {
        responseBody = "{\"errcode\":40029,\"errmsg\":\"invalid code and sensitive details\"}";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveSession(loginRequest("expired-code")));

        assertTrue(exception.getMessage().contains("40029"));
        assertFalse(exception.getMessage().contains("sensitive details"));
    }

    @Test
    void rejectsMissingOpenid() {
        responseBody = "{\"errcode\":0,\"session_key\":\"private-key\"}";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveSession(loginRequest("valid-code")));

        assertTrue(exception.getMessage().contains("openid"));
    }

    private LoginRequest loginRequest(String code) {
        LoginRequest request = new LoginRequest();
        request.setCode(code);
        return request;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
