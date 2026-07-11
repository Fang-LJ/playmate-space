package com.playmate.space.dto.collaboration;
import com.playmate.space.dto.itinerary.ItineraryResponse;
import java.util.List;
public record CollaborationSummaryResponse(String defaultTab, Long todoCount, List<CollaborationTodoResponse> todos, Long itineraryCount, Long todayItineraryCount, ItineraryResponse nextItinerary, Long activePollCount, Long pendingVoteCount, Long reviewRequiredPollCount) {}
