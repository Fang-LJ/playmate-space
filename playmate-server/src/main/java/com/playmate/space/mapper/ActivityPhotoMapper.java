package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityPhotoEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ActivityPhotoMapper extends BaseMapper<ActivityPhotoEntity> {
    @Select("SELECT * FROM t_activity_photo WHERE id = #{photoId} AND activity_id = #{activityId} AND delete_flag = 0 FOR UPDATE")
    ActivityPhotoEntity selectByIdForUpdate(@Param("activityId") Long activityId, @Param("photoId") Long photoId);

    @Update("UPDATE t_activity_photo SET like_count = like_count + 1, version = version + 1, update_time = #{now} WHERE id = #{photoId} AND status = 'ACTIVE' AND audit_status = 'APPROVED' AND visibility_status = 'NORMAL' AND delete_flag = 0")
    int incrementLikeCount(@Param("photoId") Long photoId, @Param("now") java.time.LocalDateTime now);

    @Update("UPDATE t_activity_photo SET like_count = like_count - 1, version = version + 1, update_time = #{now} WHERE id = #{photoId} AND like_count > 0 AND delete_flag = 0")
    int decrementLikeCount(@Param("photoId") Long photoId, @Param("now") java.time.LocalDateTime now);
}
