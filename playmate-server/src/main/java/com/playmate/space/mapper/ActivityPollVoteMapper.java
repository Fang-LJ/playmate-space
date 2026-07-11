package com.playmate.space.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityPollVoteEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
public interface ActivityPollVoteMapper extends BaseMapper<ActivityPollVoteEntity> {
 @Update("UPDATE t_activity_poll_vote SET delete_flag=0, update_time=#{now} WHERE poll_id=#{pollId} AND option_id=#{optionId} AND user_id=#{userId}")
 int restoreVote(@Param("pollId") Long pollId, @Param("optionId") Long optionId, @Param("userId") Long userId, @Param("now") java.time.LocalDateTime now);
}
