package com.playmate.space.dto.expense;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record SaveExpenseRequest(
  @NotBlank @Size(max=128) String title,
  @NotBlank @Size(max=32) String category,
  @NotNull @DecimalMin(value="0.01") BigDecimal amount,
  @NotNull Long payerUserId,
  @NotNull LocalDateTime expenseTime,
  @NotBlank @Size(max=32) String splitMode,
  @NotEmpty List<@Valid ExpenseShareRequest> shares,
  Long receiptFileId,
  @Size(max=512) String description,
  Integer version
) {}
