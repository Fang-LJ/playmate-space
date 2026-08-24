package com.playmate.space.dto.photo;

import java.util.List;
public record PhotoPageResponse(List<PhotoListItemResponse> items, long page, long pageSize, long total) {}
