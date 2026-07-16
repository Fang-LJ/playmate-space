package com.playmate.space.dto.todo;
import java.time.LocalDateTime;
public record ReminderAckMemberResponse(Long userId, String nickname, String status, LocalDateTime completedTime) {}
