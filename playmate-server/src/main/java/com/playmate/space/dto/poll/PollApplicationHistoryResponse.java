package com.playmate.space.dto.poll;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PollApplicationHistoryResponse(
        Long applicationId,
        Long pollId,
        Long targetItineraryId,
        Long winnerOptionId,
        Long appliedBy,
        String appliedByName,
        LocalDateTime appliedAt,
        Map<String, Object> beforeSnapshot,
        Map<String, Object> afterSnapshot,
        List<PollFieldChangeResponse> changedFields,
        List<PollUnchangedFieldResponse> unchangedFields
) {
}
