package com.playmate.space.dto.itinerary;

import com.playmate.space.dto.poll.PollListItemResponse;
import java.util.List;

public record ItineraryDetailResponse(ItineraryResponse itinerary, List<PollListItemResponse> relatedPolls) {}
