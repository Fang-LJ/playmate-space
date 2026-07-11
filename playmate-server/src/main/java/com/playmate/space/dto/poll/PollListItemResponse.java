package com.playmate.space.dto.poll;
import java.time.LocalDateTime;
public record PollListItemResponse(Long pollId, String title, String purpose, String decisionType, String voteType, String status, String resultApplyStatus, Long targetItineraryId, Long generatedItineraryId, String targetItineraryTitle, LocalDateTime deadline, Long participantCount, Boolean currentUserVoted, Long winnerOptionId) {}
