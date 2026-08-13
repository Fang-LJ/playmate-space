package com.playmate.space.dto.expense;
import java.math.BigDecimal;
import java.util.List;
public record ExpenseSummaryResponse(BigDecimal myNetAmount, BigDecimal myPaidAmount, BigDecimal myShareAmount,
                                     String mySettlementText,
                                     List<ExpenseSuggestionResponse> mySuggestions,
                                     int suggestionCount, int expenseCount,
                                     List<ExpenseListItemResponse> recentExpenses,
                                     long financeVersion) {}
