package com.playmate.space.dto.itinerary;

import java.util.List;

public record ItineraryTypeMetadataResponse(
        String type,
        String label,
        List<ItineraryFieldMetadata> focusFields,
        List<ItineraryFieldMetadata> commonFields,
        List<String> allowedDecisionTypes
) {
}
