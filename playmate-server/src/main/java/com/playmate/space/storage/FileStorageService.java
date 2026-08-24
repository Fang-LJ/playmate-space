package com.playmate.space.storage;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorageService {

    StoredFile upload(UploadFileCommand command);

    void delete(String bucketName, String objectKey);

    String generatePresignedGetUrl(String bucketName, String objectKey, Duration expiry);

    record UploadFileCommand(
            InputStream inputStream,
            String objectKey,
            String contentType,
            long size,
            boolean privateObject
    ) {
    }

    record StoredFile(
            String bucketName,
            String objectKey,
            String url
    ) {
    }
}
