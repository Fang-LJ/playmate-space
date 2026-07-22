package com.playmate.space.dto.poll;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
public record PollDetailResponse(
        Long pollId, Long activityId, String title, String description, String purpose, String decisionType,
        String voteType, Boolean allowModify, LocalDateTime deadline, String status, String resultApplyMode,
        String resultApplyStatus, Long targetItineraryId, String targetItineraryTitle, Long generatedItineraryId,
        Long winnerOptionId, Map<String, Object> targetItinerary, Map<String, Object> itineraryTemplate, List<String> decisionScope,
        List<String> decisionScopeLabels, List<String> unchangedFieldLabels, Long createdBy,
        LocalDateTime closedAt, LocalDateTime appliedAt, Long participantCount, List<Long> currentUserOptionIds,
        List<PollOptionResponse> options, PollResultPreviewResponse resultPreview,
        List<PollApplicationHistoryResponse> applicationHistory
) {}
