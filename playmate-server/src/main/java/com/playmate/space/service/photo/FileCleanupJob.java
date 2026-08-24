package com.playmate.space.service.photo;

import com.playmate.space.entity.FileEntity;
import com.playmate.space.mapper.FileMapper;
import com.playmate.space.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class FileCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(FileCleanupJob.class);

    private final FileMapper fileMapper;
    private final FileStorageService storage;
    private final PhotoProperties properties;
    private final OrphanStorageCleanupService orphanStorageCleanupService;

    public FileCleanupJob(FileMapper fileMapper, FileStorageService storage, PhotoProperties properties, OrphanStorageCleanupService orphanStorageCleanupService) {
        this.fileMapper = fileMapper;
        this.storage = storage;
        this.properties = properties;
        this.orphanStorageCleanupService = orphanStorageCleanupService;
    }

    @Scheduled(fixedDelayString = "${playmate.photo.cleanup-poll-delay:60000}")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        fileMapper.selectCleanupCandidates(now, properties.getCleanupBatchSize()).forEach(file -> cleanup(file, now));
        orphanStorageCleanupService.cleanupDue(properties.getCleanupBatchSize());
    }

    void cleanup(FileEntity candidate, LocalDateTime now) {
        if ("TEMP".equals(candidate.getLifecycleStatus())
                && fileMapper.claimExpiredTempForCleanup(candidate.getId(), now) != 1) {
            return;
        }
        FileEntity file = fileMapper.selectById(candidate.getId());
        if (file == null || !"DELETING".equals(file.getLifecycleStatus())) {
            return;
        }
        try {
            for (String objectKey : objectKeys(file)) {
                storage.delete(file.getBucketName(), objectKey);
            }
            fileMapper.markStorageDeleted(file.getId(), LocalDateTime.now());
        } catch (RuntimeException exception) {
            log.warn("Photo storage cleanup will retry: fileId={}", file.getId(), exception);
        }
    }

    private Set<String> objectKeys(FileEntity file) {
        Set<String> keys = new LinkedHashSet<>();
        if (file.getObjectKey() != null) keys.add(file.getObjectKey());
        if (file.getPreviewObjectKey() != null) keys.add(file.getPreviewObjectKey());
        if (file.getThumbObjectKey() != null) keys.add(file.getThumbObjectKey());
        return keys;
    }
}
