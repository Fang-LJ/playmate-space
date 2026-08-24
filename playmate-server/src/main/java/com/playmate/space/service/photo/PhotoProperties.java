package com.playmate.space.service.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "playmate.photo")
public class PhotoProperties {
    private Duration tempTtl = Duration.ofHours(24);
    private Duration presignedTtl = Duration.ofMinutes(20);
    private Duration auditPollDelay = Duration.ofSeconds(10);
    private int thumbnailMaxEdge = 600;
    private int previewMaxEdge = 1800;
    private int maxWidth = 6000;
    private int maxHeight = 6000;
    private long maxPixels = 24_000_000L;
    private int auditBatchSize = 20;
    private int cleanupBatchSize = 50;
    private String moderationProvider = "mock";
    private String mockResult = "APPROVE";
    public Duration getTempTtl(){return tempTtl;} public void setTempTtl(Duration v){tempTtl=v;}
    public Duration getPresignedTtl(){return presignedTtl;} public void setPresignedTtl(Duration v){presignedTtl=v;}
    public Duration getAuditPollDelay(){return auditPollDelay;} public void setAuditPollDelay(Duration v){auditPollDelay=v;}
    public int getThumbnailMaxEdge(){return thumbnailMaxEdge;} public void setThumbnailMaxEdge(int v){thumbnailMaxEdge=v;}
    public int getPreviewMaxEdge(){return previewMaxEdge;} public void setPreviewMaxEdge(int v){previewMaxEdge=v;}
    public int getMaxWidth(){return maxWidth;} public void setMaxWidth(int v){maxWidth=v;}
    public int getMaxHeight(){return maxHeight;} public void setMaxHeight(int v){maxHeight=v;}
    public long getMaxPixels(){return maxPixels;} public void setMaxPixels(long v){maxPixels=v;}
    public int getAuditBatchSize(){return auditBatchSize;} public void setAuditBatchSize(int v){auditBatchSize=v;}
    public int getCleanupBatchSize(){return cleanupBatchSize;} public void setCleanupBatchSize(int v){cleanupBatchSize=v;}
    public String getModerationProvider(){return moderationProvider;} public void setModerationProvider(String v){moderationProvider=v;}
    public String getMockResult(){return mockResult;} public void setMockResult(String v){mockResult=v;}
}
