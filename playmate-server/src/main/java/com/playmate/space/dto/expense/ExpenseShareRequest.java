package com.playmate.space.dto.expense;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record ExpenseShareRequest(@NotNull Long userId, BigDecimal shareAmount, BigDecimal splitRatio) {
    public ExpenseShareRequest(Long userId, BigDecimal shareAmount) {
        this(userId, shareAmount, null);
    }
}
