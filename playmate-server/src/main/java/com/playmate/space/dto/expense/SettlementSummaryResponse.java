package com.playmate.space.dto.expense;
import java.math.BigDecimal;
import java.util.List;
public record SettlementSummaryResponse(ExpenseSummaryResponse currentUser, List<SettlementSuggestionResponse> suggestions,
                                        List<SettlementHistoryResponse> history, List<ExpenseMemberResponse> members,
                                        BigDecimal totalExpenseAmount, int activeExpenseCount, String calculationRule) {}
