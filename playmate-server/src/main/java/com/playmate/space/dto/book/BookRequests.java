package com.playmate.space.dto.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class BookRequests {
    private BookRequests() {}
    public record Create(@NotBlank @Size(max=80) String name,
                         @NotNull @Size(max=49) List<@NotBlank @Size(max=40) String> names,
                         @NotBlank @Size(max=64) String clientRequestId) {}
    public record Rename(@NotBlank @Size(max=80) String name, @NotNull Integer version) {}
    public record State(@NotBlank @Pattern(regexp="OPEN|ARCHIVED") String status, @NotNull Integer version) {}
    public record Member(@NotBlank @Size(max=40) String nickname) {}
    public record MemberState(@NotBlank @Pattern(regexp="ACTIVE|INACTIVE") String status) {}
    public record Join(@NotBlank @Size(max=32) String code, @Positive Long memberId) {}
    public record Decision(@NotNull Boolean approve) {}
    public record Share(@NotNull @Positive Long memberId,
                        @Digits(integer=10,fraction=2) BigDecimal shareAmount,
                        @Digits(integer=12,fraction=4) BigDecimal splitRatio) {}
    public record Expense(@NotBlank @Size(max=128) String title,
                          @NotBlank @Pattern(regexp="FOOD|TRANSPORT|LODGING|TICKET|ENTERTAINMENT|SHOPPING|OTHER") String category,
                          @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
                          @NotNull @Positive Long payerMemberId,
                          @NotNull LocalDateTime expenseTime,
                          @NotBlank @Pattern(regexp="EQUAL|CUSTOM|PROPORTIONAL") String splitMode,
                          @NotEmpty @Size(max=50) List<@Valid Share> shares,
                          @Size(max=512) String description, Long receiptFileId,
                          @Size(max=64) String clientRequestId, Integer version) {}
    public record VoidExpense(@NotNull Integer expectedVersion, @Size(max=512) String reason) {}
}
