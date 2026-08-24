package com.playmate.space.service.photo;

import com.playmate.space.entity.ActivityPhotoAuditTaskEntity;
import com.playmate.space.entity.FileEntity;

public interface ContentModerationService {
    String provider();
    ModerationOutcome submitImage(ActivityPhotoAuditTaskEntity task, FileEntity file);
    record ModerationOutcome(Type type, String providerTraceId, String resultCode, String detail) {
        public enum Type { APPROVED, REJECTED, SUBMITTED, ERROR }
    }
}
