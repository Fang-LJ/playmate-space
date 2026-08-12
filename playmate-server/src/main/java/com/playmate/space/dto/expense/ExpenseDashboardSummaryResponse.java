package com.playmate.space.dto.expense;

import java.math.BigDecimal;

public record ExpenseDashboardSummaryResponse(BigDecimal totalExpenseAmount,
                                              int expenseCount,
                                              int participantCount) {}
