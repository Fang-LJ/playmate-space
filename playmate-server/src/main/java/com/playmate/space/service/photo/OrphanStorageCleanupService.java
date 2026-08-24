package com.playmate.space.service.photo;

import com.playmate.space.entity.FileOrphanCleanupTaskEntity;
import com.playmate.space.mapper.FileOrphanCleanupTaskMapper;
import com.playmate.space.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrphanStorageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(OrphanStorageCleanupService.class);
    private final FileOrphanCleanupTaskMapper taskMapper; private final FileStorageService storage;
    public OrphanStorageCleanupService(FileOrphanCleanupTaskMapper taskMapper, FileStorageService storage) { this.taskMapper = taskMapper; this.storage = storage; }
    public void recordAndCleanup(List<FileStorageService.StoredFile> files, String reason) {
        for (FileStorageService.StoredFile file : files) {
            LocalDateTime now = LocalDateTime.now(); FileOrphanCleanupTaskEntity task = new FileOrphanCleanupTaskEntity();
            task.setBucketName(file.bucketName()); task.setObjectKey(file.objectKey()); task.setStatus("PENDING"); task.setReason(reason); task.setRetryCount(0); task.setCreateTime(now); task.setUpdateTime(now); task.setDeleteFlag(0);
            try { taskMapper.insert(task); cleanup(task); } catch (RuntimeException exception) { log.error("Unable to persist orphan cleanup task: bucket={}, key={}", file.bucketName(), file.objectKey(), exception); }
        }
    }
    public void cleanupDue(int limit) { taskMapper.selectDue(LocalDateTime.now(), limit).forEach(this::cleanup); }
    private void cleanup(FileOrphanCleanupTaskEntity task) {
        try { storage.delete(task.getBucketName(), task.getObjectKey()); taskMapper.markCompleted(task.getId(), LocalDateTime.now()); }
        catch (RuntimeException exception) { log.warn("Orphan object cleanup will retry: taskId={}", task.getId(), exception); }
    }
}
