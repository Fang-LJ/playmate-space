package com.playmate.space.service.photo;

import com.playmate.space.entity.ActivityPhotoAuditTaskEntity;
import com.playmate.space.entity.FileEntity;
import org.springframework.stereotype.Service;

@Service
public class MockContentModerationService implements ContentModerationService {
    private final PhotoProperties properties;
    public MockContentModerationService(PhotoProperties properties) { this.properties = properties; }
    @Override public String provider() { return "MOCK"; }
    @Override public ModerationOutcome submitImage(ActivityPhotoAuditTaskEntity task, FileEntity file) {
        String mode = properties.getMockResult() == null ? "APPROVE" : properties.getMockResult().trim().toUpperCase();
        String trace = "mock-" + task.getId();
        return switch (mode) {
            case "REJECT" -> new ModerationOutcome(ModerationOutcome.Type.REJECTED, trace, "MOCK_REJECT", "mock moderation rejected image");
            case "ERROR" -> new ModerationOutcome(ModerationOutcome.Type.ERROR, trace, "MOCK_ERROR", "mock moderation provider error");
            default -> new ModerationOutcome(ModerationOutcome.Type.APPROVED, trace, "MOCK_APPROVE", "mock moderation approved image");
        };
    }
}
