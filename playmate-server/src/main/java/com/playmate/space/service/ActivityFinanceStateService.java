package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.entity.ActivityFinanceStateEntity;
import com.playmate.space.mapper.ActivityFinanceStateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ActivityFinanceStateService {
    private final ActivityFinanceStateMapper mapper;

    public ActivityFinanceStateService(ActivityFinanceStateMapper mapper) {
        this.mapper = mapper;
    }

    public ActivityFinanceStateEntity ensureAndLock(Long activityId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Activity finance lock requires an active transaction");
        }
        mapper.ensure(activityId);
        ActivityFinanceStateEntity state = mapper.selectForUpdate(activityId);
        if (state == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.code(), "活动费用状态初始化失败");
        }
        return state;
    }

    public long currentVersion(Long activityId) {
        Long version = mapper.selectFinanceVersion(activityId);
        return version == null ? 0L : version;
    }

    public void incrementVersion(Long activityId) {
        if (mapper.incrementVersion(activityId) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.code(), "活动费用版本更新失败");
        }
    }
}
