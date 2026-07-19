package com.playmate.space.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityItineraryEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ActivityItineraryMapper extends BaseMapper<ActivityItineraryEntity> {
    /** 行程删除是不可恢复的物理删除；调用方必须先处理关联投票。 */
    @Delete("DELETE FROM t_activity_itinerary WHERE id = #{itineraryId} AND activity_id = #{activityId}")
    int hardDelete(@Param("activityId") Long activityId, @Param("itineraryId") Long itineraryId);
}
