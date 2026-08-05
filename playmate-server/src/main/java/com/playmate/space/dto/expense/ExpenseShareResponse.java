package com.playmate.space.dto.expense;

import java.math.BigDecimal;

public record ExpenseShareResponse(Long userId, String nickname, String avatarUrl, BigDecimal shareAmount) {}
