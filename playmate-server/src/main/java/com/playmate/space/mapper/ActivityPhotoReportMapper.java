package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityPhotoReportEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ActivityPhotoReportMapper extends BaseMapper<ActivityPhotoReportEntity> {
    @Update("UPDATE t_activity_photo_report SET status = #{status}, handled_at = #{now}, update_time = #{now} "
            + "WHERE audit_task_id = #{taskId} AND status = 'PENDING' AND delete_flag = 0")
    int finishPendingByAuditTask(@Param("taskId") Long taskId, @Param("status") String status, @Param("now") LocalDateTime now);
}
