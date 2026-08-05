package com.playmate.space.dto.expense;
import java.math.BigDecimal;
import java.util.List;
public record ExpenseMemberResponse(Long userId, String nickname, String avatarUrl, BigDecimal paidAmount,
                                    BigDecimal shareAmount, BigDecimal transferredOut, BigDecimal received,
                                    BigDecimal remainingNet, String settlementText, List<ExpenseListItemResponse> paidExpenses,
                                    List<ExpenseListItemResponse> sharedExpenses, List<SettlementHistoryResponse> transfers) {}
