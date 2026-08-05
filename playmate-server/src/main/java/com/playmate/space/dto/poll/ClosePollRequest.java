package com.playmate.space.dto.poll;

/**
 * A complete-plan poll needs an explicit decision when its creator closes it.
 * General and legacy quick polls keep this field optional.
 */
public record ClosePollRequest(Long optionId) {}
