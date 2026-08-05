package com.playmate.space.dto.expense;
import jakarta.validation.constraints.Size;
public record CancelSettlementRequest(@Size(max=512) String reason) {}
