package com.playmate.space.service.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rebuildable financial facts for one activity. User-facing profile data deliberately stays out of this model.
 */
public record SettlementSnapshot(
        int schemaVersion,
        Long activityId,
        long financeVersion,
        BigDecimal totalExpenseAmount,
        int expenseCount,
        int participantCount,
        List<Account> accounts,
        List<Suggestion> suggestions,
        List<RecentExpense> recentExpenses
) {
    public static final int SCHEMA_VERSION = 1;

    public record Account(Long userId, BigDecimal paidAmount, BigDecimal shareAmount, BigDecimal netAmount) {}

    public record Suggestion(Long fromUserId, Long toUserId, BigDecimal amount) {}

    public record RecentExpense(Long expenseId, String title, String category, BigDecimal amount,
                                Long payerUserId, LocalDateTime expenseTime, Integer version,
                                List<Share> shares) {}

    public record Share(Long userId, BigDecimal shareAmount) {}
}
