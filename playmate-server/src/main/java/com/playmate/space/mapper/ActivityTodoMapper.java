package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.dto.collaboration.ActivityTodoItemResponse;
import com.playmate.space.entity.ActivityTodoEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface ActivityTodoMapper extends BaseMapper<ActivityTodoEntity> {
    @Select("""
            SELECT t.id AS todoId, t.activity_id AS activityId, a.activity_name AS activityName,
                   t.todo_type AS todoType, t.title, t.content, t.action_type AS actionType,
                   t.source_type AS sourceType, t.source_id AS sourceId, t.due_time AS dueTime,
                   tu.status AS userStatus
            FROM t_activity_todo_user tu
            INNER JOIN t_activity_todo t ON t.id = tu.todo_id AND t.delete_flag = 0
            INNER JOIN t_activity a ON a.id = t.activity_id AND a.delete_flag = 0
            INNER JOIN t_activity_member m ON m.activity_id = t.activity_id
              AND m.user_id = tu.user_id AND m.member_status = 'ACTIVE' AND m.delete_flag = 0
            WHERE tu.user_id = #{userId} AND tu.status = 'PENDING' AND tu.delete_flag = 0
              AND t.status = 'ACTIVE' AND a.status NOT IN ('CANCELED', 'ENDED')
            ORDER BY t.due_time IS NULL, t.due_time ASC, t.id ASC
            """)
    List<ActivityTodoItemResponse> selectCurrentUserPending(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(1) FROM t_activity_todo_user tu
            INNER JOIN t_activity_todo t ON t.id = tu.todo_id AND t.delete_flag = 0
            WHERE tu.user_id = #{userId} AND tu.status = 'PENDING' AND tu.delete_flag = 0
              AND t.status = 'ACTIVE'
            """)
    Long countCurrentUserPending(@Param("userId") Long userId);
}
