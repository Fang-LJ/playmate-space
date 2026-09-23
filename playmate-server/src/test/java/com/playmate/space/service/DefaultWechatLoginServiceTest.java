package com.playmate.space.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.LoginRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
    private String responseContentType = "application/json";
    private int responseStatus = 200;
    private DefaultWechatLoginService service;
    private ListAppender<ILoggingEvent> logAppender;

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
            exchange.getResponseHeaders().set("Content-Type", responseContentType);
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        service = new DefaultWechatLoginService(properties, new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort());
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(DefaultWechatLoginService.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(DefaultWechatLoginService.class)).detachAppender(logAppender);
        if (server != null) {
            server.stop(0);
        }
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
        responseBody = "{\"errcode\":40029,\"errmsg\":\"invalid code and sensitive details\",\"session_key\":\"private-key\"}";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveSession(loginRequest("expired-code")));

        assertTrue(exception.getMessage().contains("40029"));
        assertFalse(exception.getMessage().contains("sensitive details"));
        assertLogContains("errcode=40029");
        assertLogsDoNotContainSensitiveData(exception);
    }

    @Test
    void rejectsMissingOpenid() {
        responseBody = "{\"errcode\":0,\"session_key\":\"private-key\"}";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveSession(loginRequest("valid-code")));

        assertTrue(exception.getMessage().contains("openid"));
    }

    @Test
    void parsesJsonDespiteNonJsonContentType() {
        responseContentType = "text/plain; charset=utf-8";
        responseBody = "{\"openid\":\"openid-123\",\"session_key\":\"private-key\"}";

        WechatLoginService.WechatSession session = service.resolveSession(loginRequest("valid-code"));

        assertEquals("openid-123", session.openid());
        assertEquals(null, session.unionid());
        assertLogsDoNotContainSensitiveData(null);
    }

    @Test
    void logsOnlyExceptionTypesAndHttpStatusForUpstreamFailure() {
        responseStatus = 503;
        responseBody = "{\"errmsg\":\"test-secret private-key expired-code\"}";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveSession(loginRequest("expired-code")));

        assertLogContains("status=503");
        assertLogsDoNotContainSensitiveData(exception);
    }

    @Test
    void reportsNetworkFailureWithoutLoggingRequestUrl() {
        responseBody = "{}";
        server.stop(0);
        server = null;

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveSession(loginRequest("expired-code")));

        assertLogContains("org.springframework.web.client.ResourceAccessException");
        assertLogsDoNotContainSensitiveData(exception);
    }

    @Test
    void hidesMalformedResponseBodyFromLogsAndException() {
        responseBody = "{\"session_key\":\"private-key\",\"secret\":\"test-secret\",invalid}";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveSession(loginRequest("expired-code")));

        assertLogContains("parse failure: type=");
        assertLogsDoNotContainSensitiveData(exception);
    }

    private void assertLogContains(String value) {
        assertTrue(logAppender.list.stream().anyMatch(event -> event.getFormattedMessage().contains(value)));
    }

    private void assertLogsDoNotContainSensitiveData(BusinessException exception) {
        for (ILoggingEvent event : logAppender.list) {
            String message = event.getFormattedMessage();
            assertFalse(message.contains("test-secret"));
            assertFalse(message.contains("private-key"));
            assertFalse(message.contains("expired-code"));
            assertFalse(message.contains("sensitive details"));
            assertEquals(null, event.getThrowableProxy());
        }
        if (exception != null) {
            assertFalse(exception.getMessage().contains("test-secret"));
            assertFalse(exception.getMessage().contains("private-key"));
            assertFalse(exception.getMessage().contains("expired-code"));
            assertFalse(exception.getMessage().contains("sensitive details"));
        }
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
