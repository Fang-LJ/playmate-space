package com.playmate.space.service;

import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.dto.photo.CreatePhotosRequest;
import com.playmate.space.dto.photo.PhotoReportRequest;
import com.playmate.space.entity.ActivityEntity;
import com.playmate.space.entity.ActivityPhotoAuditTaskEntity;
import com.playmate.space.entity.ActivityPhotoEntity;
import com.playmate.space.entity.FileEntity;
import com.playmate.space.mapper.ActivityPhotoAuditTaskMapper;
import com.playmate.space.mapper.ActivityPhotoLikeMapper;
import com.playmate.space.mapper.ActivityPhotoMapper;
import com.playmate.space.mapper.ActivityPhotoReportMapper;
import com.playmate.space.mapper.FileMapper;
import com.playmate.space.service.photo.PhotoProperties;
import com.playmate.space.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityPhotoServiceTest {
    private static final Long ACTIVITY_ID = 10L;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    @Mock private ActivityCollaborationAccess access;
    @Mock private ActivityPhotoMapper photoMapper;
    @Mock private ActivityPhotoLikeMapper likeMapper;
    @Mock private ActivityPhotoReportMapper reportMapper;
    @Mock private ActivityPhotoAuditTaskMapper taskMapper;
    @Mock private FileMapper fileMapper;
    @Mock private ActivityMemberDisplayService displayService;
    @Mock private FileStorageService storage;

    private ActivityPhotoService service;

    @BeforeEach
    void setUp() {
        service = new ActivityPhotoService(access, photoMapper, likeMapper, reportMapper, taskMapper, fileMapper,
                displayService, storage, new PhotoProperties());
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setCreatorUserId(USER_A);
        activity.setStatus("ONGOING");
        when(access.requireUserId()).thenReturn(USER_A);
        when(access.requireActivity(ACTIVITY_ID)).thenReturn(activity);
    }

    @Test
    void bindOwnTempPhotoCreatesHiddenPendingPhotoAndInitialTask() {
        FileEntity file = bindableFile(100L, USER_A);
        when(fileMapper.selectByIdForUpdate(100L)).thenReturn(file);
        when(photoMapper.insert(any(ActivityPhotoEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ActivityPhotoEntity.class).setId(200L);
            return 1;
        });
        when(taskMapper.insert(any(ActivityPhotoAuditTaskEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ActivityPhotoAuditTaskEntity.class).setId(300L);
            return 1;
        });

        var result = service.create(ACTIVITY_ID, new CreatePhotosRequest(List.of(100L)));

        assertEquals(200L, result.getFirst().photoId());
        assertEquals("BOUND", file.getLifecycleStatus());
        assertEquals("ACTIVITY_PHOTO", file.getRelatedType());
        ArgumentCaptor<ActivityPhotoEntity> photos = ArgumentCaptor.forClass(ActivityPhotoEntity.class);
        verify(photoMapper, atLeastOnce()).insert(photos.capture());
        assertEquals("PENDING", photos.getValue().getAuditStatus());
        assertEquals("HIDDEN", photos.getValue().getVisibilityStatus());
        ArgumentCaptor<ActivityPhotoAuditTaskEntity> tasks = ArgumentCaptor.forClass(ActivityPhotoAuditTaskEntity.class);
        verify(taskMapper).insert(tasks.capture());
        assertEquals("INITIAL", tasks.getValue().getScene());
    }

    @Test
    void bindRejectsAnotherUsersTempPhoto() {
        when(fileMapper.selectByIdForUpdate(100L)).thenReturn(bindableFile(100L, USER_B));

        assertThrows(BusinessException.class, () -> service.create(ACTIVITY_ID, new CreatePhotosRequest(List.of(100L))));

        verify(photoMapper, never()).insert(any(ActivityPhotoEntity.class));
    }

    @Test
    void reportingNormalPhotoCreatesOneReviewTask() {
        ActivityPhotoEntity photo = normalPhoto(100L);
        when(photoMapper.selectByIdForUpdate(ACTIVITY_ID, 100L)).thenReturn(photo);
        when(reportMapper.selectOne(any())).thenReturn(null);
        when(reportMapper.insert(any(com.playmate.space.entity.ActivityPhotoReportEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, com.playmate.space.entity.ActivityPhotoReportEntity.class).setId(201L);
            return 1;
        });
        when(taskMapper.insert(any(ActivityPhotoAuditTaskEntity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ActivityPhotoAuditTaskEntity.class).setId(301L);
            return 1;
        });

        service.report(ACTIVITY_ID, 100L, new PhotoReportRequest("privacy"));

        assertEquals("REVIEWING", photo.getVisibilityStatus());
        assertEquals(301L, photo.getLatestAuditTaskId());
        ArgumentCaptor<ActivityPhotoAuditTaskEntity> tasks = ArgumentCaptor.forClass(ActivityPhotoAuditTaskEntity.class);
        verify(taskMapper).insert(tasks.capture());
        assertEquals("REPORT_RECHECK", tasks.getValue().getScene());
    }

    @Test
    void secondReporterDuringReviewingReusesExistingReviewTask() {
        ActivityPhotoEntity photo = normalPhoto(100L);
        photo.setVisibilityStatus("REVIEWING");
        photo.setLatestAuditTaskId(301L);
        when(photoMapper.selectByIdForUpdate(ACTIVITY_ID, 100L)).thenReturn(photo);
        when(reportMapper.selectOne(any())).thenReturn(null);

        service.report(ACTIVITY_ID, 100L, new PhotoReportRequest("OTHER"));

        ArgumentCaptor<com.playmate.space.entity.ActivityPhotoReportEntity> reports = ArgumentCaptor.forClass(com.playmate.space.entity.ActivityPhotoReportEntity.class);
        verify(reportMapper).insert(reports.capture());
        assertEquals(301L, reports.getValue().getAuditTaskId());
        verify(taskMapper, never()).insert(any(ActivityPhotoAuditTaskEntity.class));
    }

    @Test
    void repeatedLikeDoesNotIncrementCountTwice() {
        ActivityPhotoEntity photo = normalPhoto(100L);
        when(photoMapper.selectByIdForUpdate(ACTIVITY_ID, 100L)).thenReturn(photo);
        when(likeMapper.selectOne(any())).thenReturn(null);

        service.like(ACTIVITY_ID, 100L);

        verify(photoMapper).incrementLikeCount(eq(100L), any());
    }

    @Test
    void cannotLikeReviewingPhoto() {
        ActivityPhotoEntity photo = normalPhoto(100L);
        photo.setVisibilityStatus("REVIEWING");
        when(photoMapper.selectByIdForUpdate(ACTIVITY_ID, 100L)).thenReturn(photo);

        assertThrows(com.playmate.space.common.exception.ForbiddenException.class, () -> service.like(ACTIVITY_ID, 100L));
        verify(photoMapper, never()).incrementLikeCount(anyLong(), any());
    }

    private FileEntity bindableFile(Long id, Long owner) {
        FileEntity file = new FileEntity();
        file.setId(id); file.setUploadUserId(owner); file.setFileType("PHOTO"); file.setAccessLevel("PRIVATE");
        file.setLifecycleStatus("TEMP"); file.setStatus("NORMAL");
        return file;
    }

    private ActivityPhotoEntity normalPhoto(Long id) {
        ActivityPhotoEntity photo = new ActivityPhotoEntity();
        photo.setId(id); photo.setActivityId(ACTIVITY_ID); photo.setStatus("ACTIVE"); photo.setAuditStatus("APPROVED");
        photo.setVisibilityStatus("NORMAL"); photo.setLikeCount(0); photo.setVersion(0);
        return photo;
    }
}
