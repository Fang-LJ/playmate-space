package com.playmate.space.dto.expense;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
public record CompleteSettlementRequest(@NotNull Long fromUserId, @NotNull Long toUserId, @NotNull @DecimalMin(value="0.01") BigDecimal amount, @Size(max=512) String remark) {}
