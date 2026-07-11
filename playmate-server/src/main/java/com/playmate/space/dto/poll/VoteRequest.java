package com.playmate.space.dto.poll;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
public record VoteRequest(@NotEmpty(message = "至少选择一个选项") @Size(max = 20, message = "选项数量不能超过 20") List<Long> optionIds) {}
