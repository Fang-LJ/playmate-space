package com.playmate.space.dto.expense;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
public record ExpenseShareRequest(@NotNull Long userId,
                                  @Digits(integer=10, fraction=2) BigDecimal shareAmount,
                                  @Digits(integer=12, fraction=4) BigDecimal splitRatio) {
    public ExpenseShareRequest(Long userId, BigDecimal shareAmount) {
        this(userId, shareAmount, null);
    }
}
