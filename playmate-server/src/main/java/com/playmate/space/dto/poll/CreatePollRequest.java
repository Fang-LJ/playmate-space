package com.playmate.space.dto.poll;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CreatePollRequest(
        @NotBlank(message = "投票标题不能为空") @Size(max = 128, message = "投票标题长度不能超过 128") String title,
        @Size(max = 2000, message = "投票说明长度不能超过 2000") String description,
        @NotBlank(message = "投票用途不能为空") String purpose,
        @NotBlank(message = "决策类型不能为空") String decisionType,
        Long targetItineraryId,
        @NotBlank(message = "投票类型不能为空") String voteType,
        Boolean allowModify,
        LocalDateTime deadline,
        Map<String, Object> itineraryTemplate,
        List<String> decisionScope,
        @NotEmpty(message = "至少需要两个投票选项") @Size(min = 2, max = 20, message = "投票选项数量需在 2 到 20 之间") List<@Valid PollOptionRequest> options
) {}
