package com.playmate.space.dto.poll;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record UpdatePollRequest(
        @Size(max = 128, message = "投票标题长度不能超过 128") String title,
        @Size(max = 2000, message = "投票说明长度不能超过 2000") String description,
        LocalDateTime deadline,
        Boolean allowModify
) {}
