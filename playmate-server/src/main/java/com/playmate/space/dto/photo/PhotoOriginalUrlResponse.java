package com.playmate.space.dto.photo;

import java.time.LocalDateTime;
public record PhotoOriginalUrlResponse(String url, LocalDateTime expiresAt) {}
