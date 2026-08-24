package com.playmate.space.service.photo;

import com.playmate.space.entity.ActivityPhotoAuditTaskEntity;
import com.playmate.space.entity.ActivityPhotoEntity;
import com.playmate.space.entity.FileEntity;
import com.playmate.space.mapper.ActivityPhotoAuditTaskMapper;
import com.playmate.space.mapper.ActivityPhotoMapper;
import com.playmate.space.mapper.FileMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PhotoAuditService {
    private final ActivityPhotoAuditTaskMapper taskMapper; private final ActivityPhotoMapper photoMapper; private final FileMapper fileMapper;
    private final PhotoAuditResultService resultService; private final PhotoProperties properties;
    private final Map<String, ContentModerationService> providers;
    public PhotoAuditService(ActivityPhotoAuditTaskMapper taskMapper, ActivityPhotoMapper photoMapper, FileMapper fileMapper, PhotoAuditResultService resultService,
                             PhotoProperties properties, List<ContentModerationService> services) {
        this.taskMapper = taskMapper; this.photoMapper = photoMapper; this.fileMapper = fileMapper; this.resultService = resultService; this.properties = properties;
        this.providers = services.stream().collect(Collectors.toMap(service -> service.provider().toUpperCase(), Function.identity()));
    }
    @Scheduled(fixedDelayString = "${playmate.photo.audit-poll-delay:10000}")
    public void processDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.selectDueTasks(now, properties.getAuditBatchSize()).forEach(task -> process(task.getId(), now));
    }
    public void process(Long taskId, LocalDateTime now) {
        if (taskMapper.claimForSubmission(taskId, now) != 1) return;
        ActivityPhotoAuditTaskEntity task = taskMapper.selectById(taskId);
        ActivityPhotoEntity photo = task == null ? null : photoMapper.selectById(task.getPhotoId());
        FileEntity file = photo == null ? null : fileMapper.selectById(photo.getFileId());
        ContentModerationService service = providers.getOrDefault((task == null ? "" : task.getProvider()).toUpperCase(), providers.get("MOCK"));
        ContentModerationService.ModerationOutcome outcome = file == null || service == null
                ? new ContentModerationService.ModerationOutcome(ContentModerationService.ModerationOutcome.Type.ERROR, null, "FILE_OR_PROVIDER_NOT_FOUND", "审核文件或提供方不存在")
                : service.submitImage(task, file);
        try {
            if (outcome.type() == ContentModerationService.ModerationOutcome.Type.APPROVED || outcome.type() == ContentModerationService.ModerationOutcome.Type.REJECTED) resultService.apply(taskId, outcome);
            else if (outcome.type() == ContentModerationService.ModerationOutcome.Type.ERROR) resultService.retry(taskId, outcome);
        } catch (RuntimeException exception) {
            resultService.retry(taskId, new ContentModerationService.ModerationOutcome(ContentModerationService.ModerationOutcome.Type.ERROR,
                    outcome.providerTraceId(), "RESULT_APPLY_FAILED", exception.getMessage()));
        }
    }
}
