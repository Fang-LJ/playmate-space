package com.playmate.space.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityPollEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
public interface ActivityPollMapper extends BaseMapper<ActivityPollEntity> {
 @Update("UPDATE t_activity_poll SET status='CLOSED', closed_at=#{now}, update_time=#{now}, version=version+1 WHERE id=#{pollId} AND status='ACTIVE' AND delete_flag=0")
 int closeActivePoll(@Param("pollId") Long pollId, @Param("now") java.time.LocalDateTime now);
}
