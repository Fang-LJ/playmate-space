package com.playmate.space.dto.photo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePhotosRequest(@NotEmpty @Size(max = 9) List<@NotNull Long> fileIds) {}
