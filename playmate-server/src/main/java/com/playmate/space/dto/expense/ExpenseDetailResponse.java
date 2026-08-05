package com.playmate.space.dto.expense;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record ExpenseDetailResponse(Long expenseId, Long activityId, String title, String category, BigDecimal amount,
                                    Long payerUserId, String payerNickname, Long createdBy, String creatorNickname,
                                    LocalDateTime expenseTime, String splitMode, Long receiptFileId, String receiptUrl,
                                    String description, String status, String voidReason, Integer version,
                                    List<ExpenseShareResponse> shares, LocalDateTime createTime, LocalDateTime updateTime) {}
