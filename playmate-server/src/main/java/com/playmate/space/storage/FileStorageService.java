package com.playmate.space.storage;

import java.io.InputStream;

public interface FileStorageService {

    StoredFile upload(UploadFileCommand command);

    record UploadFileCommand(
            InputStream inputStream,
            String objectKey,
            String contentType,
            long size
    ) {
    }

    record StoredFile(
            String bucketName,
            String objectKey,
            String url
    ) {
    }
}
