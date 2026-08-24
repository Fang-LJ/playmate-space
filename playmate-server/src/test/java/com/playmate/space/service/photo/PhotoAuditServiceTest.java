package com.playmate.space.service.photo;

import com.playmate.space.entity.ActivityPhotoAuditTaskEntity;
import com.playmate.space.entity.ActivityPhotoEntity;
import com.playmate.space.entity.FileEntity;
import com.playmate.space.mapper.ActivityPhotoAuditTaskMapper;
import com.playmate.space.mapper.ActivityPhotoMapper;
import com.playmate.space.mapper.FileMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PhotoAuditServiceTest {
    @Test
    void unknownProviderFailsClosedInsteadOfFallingBackToMock() {
        ActivityPhotoAuditTaskMapper tasks = mock(ActivityPhotoAuditTaskMapper.class); ActivityPhotoMapper photos = mock(ActivityPhotoMapper.class); FileMapper files = mock(FileMapper.class); PhotoAuditResultService results = mock(PhotoAuditResultService.class);
        ActivityPhotoAuditTaskEntity task = task("UNKNOWN"); when(tasks.claimForSubmission(anyLong(), any())).thenReturn(1); when(tasks.selectById(1L)).thenReturn(task); when(photos.selectById(2L)).thenReturn(new ActivityPhotoEntity()); when(files.selectById(any())).thenReturn(new FileEntity());
        ContentModerationService mockProvider = new ContentModerationService() { public String provider(){return "MOCK";} public ModerationOutcome submitImage(ActivityPhotoAuditTaskEntity t, FileEntity f){return new ModerationOutcome(ModerationOutcome.Type.APPROVED, null, null, null);} };
        service(tasks, photos, files, results, List.of(mockProvider)).process(1L, LocalDateTime.now());
        verify(results).retry(eq(1L), argThat(outcome -> "PROVIDER_NOT_FOUND".equals(outcome.resultCode())));
        verify(results, never()).apply(anyLong(), any());
    }

    @Test
    void providerExceptionBecomesRetryInsteadOfLeavingSubmitted() {
        ActivityPhotoAuditTaskMapper tasks = mock(ActivityPhotoAuditTaskMapper.class); ActivityPhotoMapper photos = mock(ActivityPhotoMapper.class); FileMapper files = mock(FileMapper.class); PhotoAuditResultService results = mock(PhotoAuditResultService.class);
        ActivityPhotoAuditTaskEntity task = task("THROW"); when(tasks.claimForSubmission(anyLong(), any())).thenReturn(1); when(tasks.selectById(1L)).thenReturn(task); when(photos.selectById(2L)).thenReturn(new ActivityPhotoEntity()); when(files.selectById(any())).thenReturn(new FileEntity());
        ContentModerationService throwing = new ContentModerationService() { public String provider(){return "THROW";} public ModerationOutcome submitImage(ActivityPhotoAuditTaskEntity t, FileEntity f){throw new IllegalStateException("network");} };
        service(tasks, photos, files, results, List.of(throwing)).process(1L, LocalDateTime.now());
        verify(results).retry(eq(1L), argThat(outcome -> "PROVIDER_CALL_FAILED".equals(outcome.resultCode())));
    }

    @Test
    void timedOutSubmittedTaskIsHandedToAtomicRecovery() {
        ActivityPhotoAuditTaskMapper tasks = mock(ActivityPhotoAuditTaskMapper.class); PhotoAuditResultService results = mock(PhotoAuditResultService.class); ActivityPhotoAuditTaskEntity timedOut = task("MOCK"); timedOut.setStatus("SUBMITTED");
        when(tasks.selectDueTasks(any(), anyInt())).thenReturn(List.of()); when(tasks.selectTimedOutSubmittedTasks(any(), anyInt())).thenReturn(List.of(timedOut));
        service(tasks, mock(ActivityPhotoMapper.class), mock(FileMapper.class), results, List.of()).processDueTasks();
        verify(results).recoverTimedOutSubmission(eq(timedOut), any(), any());
    }

    private PhotoAuditService service(ActivityPhotoAuditTaskMapper tasks, ActivityPhotoMapper photos, FileMapper files, PhotoAuditResultService results, List<ContentModerationService> providers) { return new PhotoAuditService(tasks, photos, files, results, new PhotoProperties(), providers); }
    private ActivityPhotoAuditTaskEntity task(String provider) { ActivityPhotoAuditTaskEntity task = new ActivityPhotoAuditTaskEntity(); task.setId(1L); task.setPhotoId(2L); task.setProvider(provider); return task; }
}
