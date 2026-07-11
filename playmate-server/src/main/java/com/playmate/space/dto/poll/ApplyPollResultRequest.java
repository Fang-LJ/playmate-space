package com.playmate.space.dto.poll;
import jakarta.validation.constraints.NotNull;
public record ApplyPollResultRequest(@NotNull(message = "最终选项不能为空") Long optionId) {}
