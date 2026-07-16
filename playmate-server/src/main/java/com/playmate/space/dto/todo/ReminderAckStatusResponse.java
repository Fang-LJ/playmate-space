package com.playmate.space.dto.todo;
import java.util.List;
public record ReminderAckStatusResponse(Long todoId, Long acknowledgedCount, Long pendingCount, List<ReminderAckMemberResponse> members) {}
