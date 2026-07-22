package com.playmate.space.dto.poll;

public record PollUnchangedFieldResponse(
        String field,
        String label,
        Object value
) {
}
