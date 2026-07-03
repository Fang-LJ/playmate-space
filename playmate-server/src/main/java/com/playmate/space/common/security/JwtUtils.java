package com.playmate.space.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtils {

    private static final String USER_ID = "userId";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtUtils(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(sha256(properties.getSecret()));
    }

    public String generateToken(Long userId) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(properties.getExpireSeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(USER_ID, userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object userId = claims.get(USER_ID);
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(claims.getSubject());
    }

    private byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
