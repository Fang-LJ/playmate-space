package com.playmate.space.dto.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateActivityReminderRequest(
        @NotBlank(message = "提醒标题不能为空") @Size(max = 128, message = "提醒标题不能超过 128 个字符") String title,
        @Size(max = 512, message = "提醒内容不能超过 512 个字符") String content,
        LocalDateTime dueTime) {}
