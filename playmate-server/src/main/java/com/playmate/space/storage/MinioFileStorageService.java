package com.playmate.space.storage;

import com.playmate.space.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

@Service
public class MinioFileStorageService implements FileStorageService {

    private final MinioProperties properties;
    private final MinioClient minioClient;

    public MinioFileStorageService(MinioProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Override
    public StoredFile upload(UploadFileCommand command) {
        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(command.objectKey())
                    .stream(command.inputStream(), command.size(), -1)
                    .contentType(command.contentType())
                    .build());
            return new StoredFile(properties.getBucket(), command.objectKey(), buildPublicUrl(command.objectKey()));
        } catch (Exception exception) {
            throw new BusinessException("文件上传失败");
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.getBucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
        }
    }

    private String buildPublicUrl(String objectKey) {
        String baseUrl = properties.getPublicBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/" + properties.getBucket() + "/" + objectKey;
    }
}
