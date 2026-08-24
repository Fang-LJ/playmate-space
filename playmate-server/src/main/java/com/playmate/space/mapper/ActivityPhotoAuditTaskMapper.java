package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityPhotoAuditTaskEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

public interface ActivityPhotoAuditTaskMapper extends BaseMapper<ActivityPhotoAuditTaskEntity> {
    @Select("SELECT * FROM t_activity_photo_audit_task WHERE delete_flag = 0 AND (status = 'PENDING' OR (status = 'RETRY_WAIT' AND next_retry_time <= #{now})) ORDER BY id ASC LIMIT #{limit}")
    List<ActivityPhotoAuditTaskEntity> selectDueTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("SELECT * FROM t_activity_photo_audit_task WHERE delete_flag = 0 AND status = 'SUBMITTED' AND submitted_at IS NOT NULL AND submitted_at <= #{threshold} ORDER BY id ASC LIMIT #{limit}")
    List<ActivityPhotoAuditTaskEntity> selectTimedOutSubmittedTasks(@Param("threshold") LocalDateTime threshold, @Param("limit") int limit);

    @Update("UPDATE t_activity_photo_audit_task SET status = 'SUBMITTED', submitted_at = #{now}, update_time = #{now} WHERE id = #{taskId} AND delete_flag = 0 AND (status = 'PENDING' OR (status = 'RETRY_WAIT' AND next_retry_time <= #{now}))")
    int claimForSubmission(@Param("taskId") Long taskId, @Param("now") LocalDateTime now);

    @Update("UPDATE t_activity_photo_audit_task SET status = 'RETRY_WAIT', retry_count = retry_count + 1, next_retry_time = #{nextRetryTime}, last_error = 'SUBMITTED_TIMEOUT', update_time = #{now} WHERE id = #{taskId} AND delete_flag = 0 AND status = 'SUBMITTED' AND submitted_at IS NOT NULL AND submitted_at <= #{threshold}")
    int recoverTimedOutSubmission(@Param("taskId") Long taskId, @Param("threshold") LocalDateTime threshold,
                                  @Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM t_activity_photo_audit_task WHERE id = #{taskId} AND delete_flag = 0 FOR UPDATE")
    ActivityPhotoAuditTaskEntity selectByIdForUpdate(@Param("taskId") Long taskId);
}
