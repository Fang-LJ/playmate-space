package com.playmate.space.dto.expense;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ExpenseListItemResponse(Long expenseId, String title, String category, BigDecimal amount, String payerNickname,
                                      Long payerUserId, LocalDateTime expenseTime, Integer shareMemberCount,
                                      BigDecimal currentUserShareAmount, String status, Integer version) {}
