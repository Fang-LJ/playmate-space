package com.playmate.space.dto.expense;

import java.util.List;

public record ExpenseDashboardResponse(ExpenseDashboardSummaryResponse summary,
                                       List<ExpenseDashboardMemberResponse> members,
                                       List<ExpenseSuggestionResponse> suggestions,
                                       String calculationRule) {}
