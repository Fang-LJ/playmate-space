package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityPhotoLikeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.Collection;
import java.util.List;

public interface ActivityPhotoLikeMapper extends BaseMapper<ActivityPhotoLikeEntity> {
    @Insert("INSERT IGNORE INTO t_activity_photo_like (activity_id, photo_id, user_id, status, liked_at, create_time, update_time, delete_flag) VALUES (#{activityId}, #{photoId}, #{userId}, 'ACTIVE', #{now}, #{now}, #{now}, 0)")
    int insertIfAbsent(@Param("activityId") Long activityId, @Param("photoId") Long photoId, @Param("userId") Long userId, @Param("now") java.time.LocalDateTime now);

    @Update("UPDATE t_activity_photo_like SET status = 'ACTIVE', liked_at = #{now}, canceled_at = NULL, update_time = #{now} WHERE photo_id = #{photoId} AND user_id = #{userId} AND status = 'CANCELED' AND delete_flag = 0")
    int reactivateIfCanceled(@Param("photoId") Long photoId, @Param("userId") Long userId, @Param("now") java.time.LocalDateTime now);

    @Update("UPDATE t_activity_photo_like SET status = 'CANCELED', canceled_at = #{now}, update_time = #{now} WHERE photo_id = #{photoId} AND user_id = #{userId} AND status = 'ACTIVE' AND delete_flag = 0")
    int cancelIfActive(@Param("photoId") Long photoId, @Param("userId") Long userId, @Param("now") java.time.LocalDateTime now);

    @Select("<script>SELECT photo_id FROM t_activity_photo_like WHERE user_id = #{userId} AND status = 'ACTIVE' AND delete_flag = 0 AND photo_id IN <foreach collection='photoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectActivePhotoIds(@Param("userId") Long userId, @Param("photoIds") Collection<Long> photoIds);
}
