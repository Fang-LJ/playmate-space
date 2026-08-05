package com.playmate.space.dto.expense;
import java.math.BigDecimal;
import java.util.List;
public record ExpenseSummaryResponse(BigDecimal pendingPayAmount, BigDecimal pendingReceiveAmount, BigDecimal netSettlement,
                                     String settlementText, List<SettlementSuggestionResponse> mySuggestions,
                                     int pendingSettlementCount, int completedSettlementCount, BigDecimal completedSettlementAmount,
                                     BigDecimal totalExpenseAmount, int activeExpenseCount, List<ExpenseListItemResponse> recentExpenses) {}
