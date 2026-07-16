package com.playmate.space.service;

import com.playmate.space.dto.collaboration.ActivityTodoItemResponse;
import com.playmate.space.dto.collaboration.UserActivityTodosResponse;
import com.playmate.space.mapper.ActivityTodoMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** Read model for persisted todo lifecycles. No poll, vote, or itinerary scanning occurs here. */
@Service
public class ActivityTodoService {
    private final ActivityCollaborationAccess access;
    private final ActivityTodoMapper todoMapper;
    public ActivityTodoService(ActivityCollaborationAccess access, ActivityTodoMapper todoMapper) {
        this.access = access; this.todoMapper = todoMapper;
    }
    public UserActivityTodosResponse getCurrentUserTodos() {
        Long userId = access.requireUserId(); List<ActivityTodoItemResponse> todos = todoMapper.selectCurrentUserPending(userId);
        return new UserActivityTodosResponse((long) todos.size(), todos);
    }
    public List<ActivityTodoItemResponse> getForActivity(Long activityId, Long userId) {
        return todoMapper.selectCurrentUserPending(userId).stream().filter(todo -> activityId.equals(todo.getActivityId())).toList();
    }
}
