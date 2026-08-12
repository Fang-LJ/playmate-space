package com.playmate.space.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityExpenseEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ActivityExpenseMapper extends BaseMapper<ActivityExpenseEntity> {
    @Update("""
            UPDATE t_activity_expense
            SET title = #{expense.title},
                category = #{expense.category},
                amount = #{expense.amount},
                payer_user_id = #{expense.payerUserId},
                split_mode = #{expense.splitMode},
                expense_time = #{expense.expenseTime},
                receipt_file_id = #{expense.receiptFileId},
                description = #{expense.description},
                version = version + 1,
                update_time = #{updateTime}
            WHERE id = #{expense.id}
              AND activity_id = #{expense.activityId}
              AND version = #{expectedVersion}
              AND status = 'ACTIVE'
              AND delete_flag = 0
            """)
    int updateActiveByVersion(@Param("expense") ActivityExpenseEntity expense,
                              @Param("expectedVersion") Integer expectedVersion,
                              @Param("updateTime") LocalDateTime updateTime);
}
