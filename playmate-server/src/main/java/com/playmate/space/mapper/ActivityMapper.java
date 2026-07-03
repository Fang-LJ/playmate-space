package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.dto.activity.ActivityListItemResponse;
import com.playmate.space.entity.ActivityEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ActivityMapper extends BaseMapper<ActivityEntity> {

    @Select("""
            SELECT
              a.id AS activityId,
              a.activity_name AS name,
              a.activity_type AS type,
              a.status AS status,
              a.cover_url AS coverUrl,
              a.location_name AS locationName,
              a.start_date AS startDate,
              a.end_date AS endDate,
              a.member_count AS memberCount,
              m.role AS role,
              a.create_time AS createTime,
              a.update_time AS updateTime
            FROM t_activity_member m
            INNER JOIN t_activity a ON a.id = m.activity_id
            WHERE m.user_id = #{userId}
              AND m.member_status = 'ACTIVE'
              AND m.delete_flag = 0
              AND a.delete_flag = 0
            ORDER BY a.update_time DESC, a.create_time DESC
            """)
    List<ActivityListItemResponse> selectMyActivities(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM t_activity WHERE share_code = #{shareCode}")
    Long countByShareCodeIncludeDeleted(@Param("shareCode") String shareCode);
}
