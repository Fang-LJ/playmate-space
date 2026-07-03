package com.playmate.space.service;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import com.playmate.space.common.exception.UnauthorizedException;
import com.playmate.space.common.security.LoginUserContext;
import com.playmate.space.entity.FileEntity;
import com.playmate.space.mapper.FileMapper;
import com.playmate.space.storage.FileStorageService;
import com.playmate.space.vo.FileUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final String FILE_TYPE_ACTIVITY_COVER = "ACTIVITY_COVER";
    private static final String FILE_STATUS_NORMAL = "NORMAL";
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final FileMapper fileMapper;
    private final FileStorageService fileStorageService;

    public FileService(FileMapper fileMapper, FileStorageService fileStorageService) {
        this.fileMapper = fileMapper;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public FileUploadResponse upload(MultipartFile file, String fileType) {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException();
        }
        validateFileType(fileType);
        validateFile(file);

        String contentType = resolveContentType(file);
        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        String objectKey = generateObjectKey(userId, extension);

        FileStorageService.StoredFile storedFile;
        try {
            storedFile = fileStorageService.upload(new FileStorageService.UploadFileCommand(
                    file.getInputStream(),
                    objectKey,
                    contentType,
                    file.getSize()
            ));
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        }

        FileEntity entity = buildFileEntity(file, fileType, userId, contentType, extension, storedFile);
        fileMapper.insert(entity);

        return buildResponse(entity);
    }

    private void validateFileType(String fileType) {
        if (!FILE_TYPE_ACTIVITY_COVER.equals(fileType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "fileType 只支持 ACTIVITY_COVER");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "文件大小不能超过 5MB");
        }

        String contentType = resolveContentType(file);
        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType) || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.code(), "只支持 jpg、jpeg、png、webp 图片");
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename)) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
                return originalFilename.substring(dotIndex + 1).trim().toLowerCase(Locale.ROOT);
            }
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "";
        };
    }

    private String generateObjectKey(Long userId, String extension) {
        return "files/" + LocalDate.now().format(DATE_FORMATTER)
                + "/" + userId
                + "/" + UUID.randomUUID()
                + "." + extension;
    }

    private FileEntity buildFileEntity(
            MultipartFile file,
            String fileType,
            Long userId,
            String contentType,
            String extension,
            FileStorageService.StoredFile storedFile
    ) {
        LocalDateTime now = LocalDateTime.now();
        FileEntity entity = new FileEntity();
        entity.setFileType(fileType);
        entity.setBucketName(storedFile.bucketName());
        entity.setObjectKey(storedFile.objectKey());
        entity.setUrl(storedFile.url());
        entity.setOriginalName(file.getOriginalFilename());
        entity.setFileExt(extension);
        entity.setSize(file.getSize());
        entity.setContentType(contentType);
        entity.setUploadUserId(userId);
        entity.setStatus(FILE_STATUS_NORMAL);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleteFlag(0);
        return entity;
    }

    private FileUploadResponse buildResponse(FileEntity entity) {
        FileUploadResponse response = new FileUploadResponse();
        response.setFileId(entity.getId());
        response.setUrl(entity.getUrl());
        response.setObjectKey(entity.getObjectKey());
        response.setContentType(entity.getContentType());
        response.setSize(entity.getSize());
        return response;
    }
}
