package com.playmate.space.dto.photo;

import jakarta.validation.constraints.NotBlank;
public record PhotoReportRequest(@NotBlank String reasonCode) {}
