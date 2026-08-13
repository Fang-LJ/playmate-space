package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityFinanceStateEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ActivityFinanceStateMapper extends BaseMapper<ActivityFinanceStateEntity> {
    @Insert("""
            INSERT INTO t_activity_finance_state(activity_id, finance_version, create_time, update_time)
            VALUES (#{activityId}, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE activity_id = activity_id
            """)
    int ensure(@Param("activityId") Long activityId);

    @Select("""
            SELECT activity_id, finance_version, create_time, update_time
            FROM t_activity_finance_state
            WHERE activity_id = #{activityId}
            FOR UPDATE
            """)
    ActivityFinanceStateEntity selectForUpdate(@Param("activityId") Long activityId);

    @Select("SELECT finance_version FROM t_activity_finance_state WHERE activity_id = #{activityId}")
    Long selectFinanceVersion(@Param("activityId") Long activityId);

    @Update("""
            UPDATE t_activity_finance_state
            SET finance_version = finance_version + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE activity_id = #{activityId}
            """)
    int incrementVersion(@Param("activityId") Long activityId);
}
