package com.playmate.space.dto.poll;

import java.util.List;

public record PollResultPreviewResponse(
        Long pollId,
        Long optionId,
        String optionText,
        Long targetItineraryId,
        String targetItineraryTitle,
        List<PollFieldChangeResponse> changedFields,
        List<PollUnchangedFieldResponse> unchangedFields,
        Boolean canApply
) {
}
