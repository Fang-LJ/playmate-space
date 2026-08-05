package com.playmate.space.dto.expense;
import java.math.BigDecimal;
public record SettlementSuggestionResponse(Long fromUserId, String fromNickname, Long toUserId, String toNickname,
                                           BigDecimal amount, boolean currentUserCanComplete, boolean currentUserRelated) {}
