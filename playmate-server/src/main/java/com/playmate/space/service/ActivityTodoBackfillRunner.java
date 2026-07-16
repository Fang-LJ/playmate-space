package com.playmate.space.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.playmate.space.entity.ActivityPollEntity;
import com.playmate.space.mapper.ActivityPollMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in only: set PLAYMATE_TODO_BACKFILL_ON_STARTUP=true once after applying the migration. */
@Configuration
public class ActivityTodoBackfillRunner {
    private static final Logger log = LoggerFactory.getLogger(ActivityTodoBackfillRunner.class);
    @Bean
    ApplicationRunner activityTodoBackfill(@Value("${playmate.todo.backfill-on-startup:false}") boolean enabled,
                                            ActivityPollMapper pollMapper, ActivityTodoLifecycleService lifecycleService) {
        return args -> {
            if (!enabled) return;
            int count = lifecycleService.backfillActivePollTodos(pollMapper.selectList(new LambdaQueryWrapper<ActivityPollEntity>()
                    .in(ActivityPollEntity::getStatus, "ACTIVE", "CLOSED")
                    .or(wrapper -> wrapper.eq(ActivityPollEntity::getResultApplyStatus, "REVIEW_REQUIRED"))));
            log.info("activity todo backfill completed, processed polls={}", count);
        };
    }
}
