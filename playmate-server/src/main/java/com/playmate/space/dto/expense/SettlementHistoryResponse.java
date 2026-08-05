package com.playmate.space.dto.expense;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record SettlementHistoryResponse(Long settlementId, Long fromUserId, String fromNickname, Long toUserId,
                                        String toNickname, BigDecimal amount, String status, LocalDateTime completedAt,
                                        LocalDateTime canceledAt, Long operatedBy, String remark) {}
