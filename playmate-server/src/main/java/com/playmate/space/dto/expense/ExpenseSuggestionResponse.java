package com.playmate.space.dto.expense;

import java.math.BigDecimal;

public record ExpenseSuggestionResponse(Long fromUserId, String fromNickname,
                                        String fromAvatarUrl, Long toUserId, String toNickname,
                                        String toAvatarUrl,
                                        BigDecimal amount) {}
