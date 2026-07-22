package com.playmate.space.dto.poll;

public record PollFieldChangeResponse(
        String field,
        String label,
        Object beforeValue,
        Object afterValue
) {
}
