package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.FileEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface FileMapper extends BaseMapper<FileEntity> {
    @Select("SELECT * FROM t_file WHERE id = #{fileId} AND delete_flag = 0 FOR UPDATE")
    FileEntity selectByIdForUpdate(@Param("fileId") Long fileId);

    @Select("SELECT * FROM t_file WHERE delete_flag = 0 AND ((lifecycle_status = 'TEMP' AND expire_at IS NOT NULL AND expire_at <= #{now}) OR lifecycle_status = 'DELETING') ORDER BY id ASC LIMIT #{limit}")
    List<FileEntity> selectCleanupCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE t_file SET lifecycle_status = 'DELETING', update_time = #{now} WHERE id = #{fileId} AND lifecycle_status = 'TEMP' AND expire_at IS NOT NULL AND expire_at <= #{now} AND delete_flag = 0")
    int claimExpiredTempForCleanup(@Param("fileId") Long fileId, @Param("now") LocalDateTime now);

    @Update("UPDATE t_file SET lifecycle_status = 'DELETED', status = 'DELETED', update_time = #{now} WHERE id = #{fileId} AND lifecycle_status = 'DELETING' AND delete_flag = 0")
    int markStorageDeleted(@Param("fileId") Long fileId, @Param("now") LocalDateTime now);
}
