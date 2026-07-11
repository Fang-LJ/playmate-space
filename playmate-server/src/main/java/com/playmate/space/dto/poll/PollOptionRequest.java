package com.playmate.space.dto.poll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record PollOptionRequest(
        @NotBlank(message = "投票选项不能为空") @Size(max = 255, message = "投票选项长度不能超过 255") String optionText,
        @Size(max = 512, message = "选项说明长度不能超过 512") String optionDescription,
        Map<String, Object> resultPayload
) {}
