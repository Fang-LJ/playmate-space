package com.playmate.space.dto.expense;

import java.math.BigDecimal;

public record ExpenseSuggestionResponse(Long fromUserId, String fromNickname,
                                        Long toUserId, String toNickname,
                                        BigDecimal amount) {}
