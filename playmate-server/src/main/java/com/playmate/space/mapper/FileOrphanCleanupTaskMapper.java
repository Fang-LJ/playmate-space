package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.FileOrphanCleanupTaskEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

public interface FileOrphanCleanupTaskMapper extends BaseMapper<FileOrphanCleanupTaskEntity> {
    @Select("SELECT * FROM t_file_orphan_cleanup_task WHERE delete_flag = 0 AND status = 'PENDING' AND (next_retry_time IS NULL OR next_retry_time <= #{now}) ORDER BY id ASC LIMIT #{limit}")
    List<FileOrphanCleanupTaskEntity> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
    @Update("UPDATE t_file_orphan_cleanup_task SET status = 'COMPLETED', completed_at = #{now}, update_time = #{now} WHERE id = #{id} AND status = 'PENDING'")
    int markCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);
}
