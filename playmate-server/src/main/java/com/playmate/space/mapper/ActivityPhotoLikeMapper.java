package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityPhotoLikeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.Collection;
import java.util.List;

public interface ActivityPhotoLikeMapper extends BaseMapper<ActivityPhotoLikeEntity> {
    @Select("<script>SELECT photo_id FROM t_activity_photo_like WHERE user_id = #{userId} AND status = 'ACTIVE' AND delete_flag = 0 AND photo_id IN <foreach collection='photoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectActivePhotoIds(@Param("userId") Long userId, @Param("photoIds") Collection<Long> photoIds);
}
