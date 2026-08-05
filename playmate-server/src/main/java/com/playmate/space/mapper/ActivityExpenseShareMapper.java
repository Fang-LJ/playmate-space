package com.playmate.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityExpenseShareEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ActivityExpenseShareMapper extends BaseMapper<ActivityExpenseShareEntity> {
    // The unique key is (expense_id, user_id), so replacing a bill's shares must remove old rows.
    @Delete("DELETE FROM t_activity_expense_share WHERE expense_id = #{expenseId}")
    int deleteByExpenseId(@Param("expenseId") Long expenseId);
}
