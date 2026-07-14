package com.playmate.space.dto.collaboration;

import java.util.List;

public record UserActivityTodosResponse(Long todoCount, List<ActivityTodoItemResponse> todos) {}
