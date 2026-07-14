package com.playmate.space.dto.collaboration;

import java.time.LocalDateTime;

public record ActivityTodoItemResponse(
        Long activityId,
        String activityName,
        String targetType,
        Long targetId,
        String todoType,
        String title,
        String description,
        LocalDateTime dueAt,
        String actionText
) {}
