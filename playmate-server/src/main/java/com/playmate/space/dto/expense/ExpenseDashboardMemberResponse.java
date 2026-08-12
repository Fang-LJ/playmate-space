package com.playmate.space.dto.expense;

import java.math.BigDecimal;

public record ExpenseDashboardMemberResponse(Long userId, String nickname, String avatarUrl,
                                             BigDecimal paidAmount, BigDecimal shareAmount,
                                             BigDecimal netAmount, String settlementText) {}
