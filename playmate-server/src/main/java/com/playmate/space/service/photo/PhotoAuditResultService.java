package com.playmate.space.service.photo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playmate.space.entity.*;
import com.playmate.space.mapper.ActivityPhotoAuditTaskMapper;
import com.playmate.space.mapper.ActivityPhotoMapper;
import com.playmate.space.mapper.ActivityPhotoReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PhotoAuditResultService {
    private final ActivityPhotoAuditTaskMapper taskMapper;
    private final ActivityPhotoMapper photoMapper;
    private final ActivityPhotoReportMapper reportMapper;
    private final ObjectMapper objectMapper;
    public PhotoAuditResultService(ActivityPhotoAuditTaskMapper taskMapper, ActivityPhotoMapper photoMapper, ActivityPhotoReportMapper reportMapper,
                                   ObjectMapper objectMapper) {
        this.taskMapper = taskMapper; this.photoMapper = photoMapper; this.reportMapper = reportMapper; this.objectMapper = objectMapper;
    }

    @Transactional
    public void apply(Long taskId, ContentModerationService.ModerationOutcome outcome) {
        // 全链路固定锁序：photo -> audit_task -> report -> file。
        ActivityPhotoAuditTaskEntity snapshot = taskMapper.selectById(taskId);
        if (snapshot == null) return;
        ActivityPhotoEntity photo = photoMapper.selectByIdForUpdate(snapshot.getActivityId(), snapshot.getPhotoId());
        ActivityPhotoAuditTaskEntity task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || completed(task.getStatus()) || "CANCELED".equals(task.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        boolean approved = outcome.type() == ContentModerationService.ModerationOutcome.Type.APPROVED;
        task.setStatus(approved ? "APPROVED" : "REJECTED"); task.setProviderTraceId(outcome.providerTraceId());
        task.setResultCode(outcome.resultCode()); task.setResultDetail(jsonDetail(outcome.detail())); task.setCompletedAt(now); task.setUpdateTime(now);
        taskMapper.updateById(task);

        if (photo == null || !task.getId().equals(photo.getLatestAuditTaskId()) || !"ACTIVE".equals(photo.getStatus())) return;
        if ("INITIAL".equals(task.getScene())) {
            photo.setAuditStatus(approved ? "APPROVED" : "REJECTED");
            photo.setVisibilityStatus(approved ? "NORMAL" : "HIDDEN");
        } else if ("REPORT_RECHECK".equals(task.getScene())) {
            reportMapper.finishPendingByAuditTask(task.getId(), approved ? "DISMISSED" : "CONFIRMED", now);
            if (approved) photo.setVisibilityStatus("NORMAL");
            else { photo.setAuditStatus("REJECTED"); photo.setVisibilityStatus("HIDDEN"); }
        }
        photo.setVersion(photo.getVersion() == null ? 1 : photo.getVersion() + 1); photo.setUpdateTime(now); photoMapper.updateById(photo);
    }

    @Transactional
    public void retry(Long taskId, ContentModerationService.ModerationOutcome outcome) {
        ActivityPhotoAuditTaskEntity task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || completed(task.getStatus()) || "CANCELED".equals(task.getStatus())) return;
        int attempts = task.getRetryCount() + 1;
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("RETRY_WAIT"); task.setRetryCount(attempts); task.setNextRetryTime(now.plusMinutes(backoffMinutes(attempts)));
        task.setProviderTraceId(outcome.providerTraceId()); task.setResultCode(outcome.resultCode()); task.setLastError(outcome.detail()); task.setUpdateTime(now);
        taskMapper.updateById(task);
    }

    @Transactional
    public void recoverTimedOutSubmission(ActivityPhotoAuditTaskEntity task, LocalDateTime threshold, LocalDateTime now) {
        if (task == null || task.getRetryCount() == null) return;
        int attempts = task.getRetryCount() + 1;
        taskMapper.recoverTimedOutSubmission(task.getId(), threshold, now.plusMinutes(backoffMinutes(attempts)), now);
    }

    private long backoffMinutes(int attempts) { return attempts == 1 ? 1 : attempts == 2 ? 5 : attempts == 3 ? 15 : 30; }

    private boolean completed(String status) { return "APPROVED".equals(status) || "REJECTED".equals(status); }

    private String jsonDetail(String detail) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("message", detail == null ? "" : detail));
        } catch (JsonProcessingException exception) {
            return "{\"message\":\"审核结果详情序列化失败\"}";
        }
    }
}
