package com.playmate.space.service.photo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playmate.space.entity.ActivityPhotoAuditTaskEntity;
import com.playmate.space.entity.ActivityPhotoEntity;
import com.playmate.space.mapper.ActivityPhotoAuditTaskMapper;
import com.playmate.space.mapper.ActivityPhotoMapper;
import com.playmate.space.mapper.ActivityPhotoReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoAuditResultServiceTest {
    @Mock private ActivityPhotoAuditTaskMapper taskMapper;
    @Mock private ActivityPhotoMapper photoMapper;
    @Mock private ActivityPhotoReportMapper reportMapper;

    @Test
    void initialApprovalMakesLatestActivePhotoVisibleAndStoresJsonDetail() {
        ActivityPhotoAuditTaskEntity task = task(10L, "INITIAL");
        ActivityPhotoEntity photo = photo(10L);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(photoMapper.selectByIdForUpdate(1L, 2L)).thenReturn(photo);

        service().apply(10L, approved());

        assertEquals("APPROVED", task.getStatus());
        assertEquals("APPROVED", photo.getAuditStatus());
        assertEquals("NORMAL", photo.getVisibilityStatus());
        assertEquals("{\"message\":\"approved\"}", task.getResultDetail());
    }

    @Test
    void reportRejectionHidesPhotoAndConfirmsAllReportsOnTheTask() {
        ActivityPhotoAuditTaskEntity task = task(10L, "REPORT_RECHECK");
        ActivityPhotoEntity photo = photo(10L);
        photo.setAuditStatus("APPROVED"); photo.setVisibilityStatus("REVIEWING");
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(photoMapper.selectByIdForUpdate(1L, 2L)).thenReturn(photo);

        service().apply(10L, new ContentModerationService.ModerationOutcome(ContentModerationService.ModerationOutcome.Type.REJECTED,
                "trace", "REJECT", "rejected"));

        assertEquals("REJECTED", photo.getAuditStatus());
        assertEquals("HIDDEN", photo.getVisibilityStatus());
        verify(reportMapper).finishPendingByAuditTask(anyLong(), org.mockito.ArgumentMatchers.eq("CONFIRMED"), any());
    }

    @Test
    void delayedOldTaskCannotOverwriteNewerPhotoState() {
        ActivityPhotoAuditTaskEntity task = task(10L, "INITIAL");
        ActivityPhotoEntity photo = photo(11L);
        when(taskMapper.selectByIdForUpdate(10L)).thenReturn(task);
        when(photoMapper.selectByIdForUpdate(1L, 2L)).thenReturn(photo);

        service().apply(10L, approved());

        verify(photoMapper, never()).updateById(any(ActivityPhotoEntity.class));
    }

    private PhotoAuditResultService service() {
        return new PhotoAuditResultService(taskMapper, photoMapper, reportMapper, new ObjectMapper());
    }

    private ActivityPhotoAuditTaskEntity task(Long id, String scene) {
        ActivityPhotoAuditTaskEntity task = new ActivityPhotoAuditTaskEntity();
        task.setId(id); task.setActivityId(1L); task.setPhotoId(2L); task.setScene(scene); task.setStatus("SUBMITTED"); task.setRetryCount(0);
        return task;
    }

    private ActivityPhotoEntity photo(Long latestTaskId) {
        ActivityPhotoEntity photo = new ActivityPhotoEntity();
        photo.setId(2L); photo.setActivityId(1L); photo.setStatus("ACTIVE"); photo.setAuditStatus("PENDING");
        photo.setVisibilityStatus("HIDDEN"); photo.setLatestAuditTaskId(latestTaskId); photo.setVersion(0);
        return photo;
    }

    private ContentModerationService.ModerationOutcome approved() {
        return new ContentModerationService.ModerationOutcome(ContentModerationService.ModerationOutcome.Type.APPROVED,
                "trace", "APPROVE", "approved");
    }
}
