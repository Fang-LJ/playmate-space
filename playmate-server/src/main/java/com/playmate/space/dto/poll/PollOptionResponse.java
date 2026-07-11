package com.playmate.space.dto.poll;
import java.util.Map;
public record PollOptionResponse(Long optionId, String optionText, String optionDescription, Map<String, Object> resultPayload, Integer sortNo, Long voteCount, Boolean selectedByCurrentUser) {}
