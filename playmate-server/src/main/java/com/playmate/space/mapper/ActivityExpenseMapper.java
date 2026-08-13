package com.playmate.space.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.playmate.space.entity.ActivityExpenseEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ActivityExpenseMapper extends BaseMapper<ActivityExpenseEntity> {
    @Select("""
            SELECT *
            FROM t_activity_expense
            WHERE activity_id = #{activityId}
              AND created_by = #{createdBy}
              AND client_request_id = #{clientRequestId}
              AND delete_flag = 0
            LIMIT 1
            FOR UPDATE
            """)
    ActivityExpenseEntity selectByCreateRequest(@Param("activityId") Long activityId,
                                                @Param("createdBy") Long createdBy,
                                                @Param("clientRequestId") String clientRequestId);

    @Select("""
            SELECT *
            FROM t_activity_expense
            WHERE id = #{expenseId}
              AND activity_id = #{activityId}
              AND delete_flag = 0
            FOR UPDATE
            """)
    ActivityExpenseEntity selectByIdForUpdate(@Param("activityId") Long activityId,
                                              @Param("expenseId") Long expenseId);

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

    @Update("""
            UPDATE t_activity_expense
            SET status = 'VOID',
                voided_by = #{voidedBy},
                voided_at = #{voidedAt},
                void_reason = #{voidReason},
                version = version + 1,
                update_time = #{updateTime}
            WHERE id = #{expenseId}
              AND activity_id = #{activityId}
              AND version = #{expectedVersion}
              AND status = 'ACTIVE'
              AND delete_flag = 0
            """)
    int voidActiveByVersion(@Param("activityId") Long activityId,
                            @Param("expenseId") Long expenseId,
                            @Param("expectedVersion") Integer expectedVersion,
                            @Param("voidedBy") Long voidedBy,
                            @Param("voidedAt") LocalDateTime voidedAt,
                            @Param("voidReason") String voidReason,
                            @Param("updateTime") LocalDateTime updateTime);
}
