package com.playmate.space.dto.expense;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record VoidExpenseRequest(@NotNull Integer expectedVersion, @Size(max=512) String reason) {}
