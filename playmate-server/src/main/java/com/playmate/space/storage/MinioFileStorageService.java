package com.playmate.space.storage;

import com.playmate.space.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Service;

import java.time.Duration;

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
            String bucketName = command.privateObject() ? properties.getPrivateBucket() : properties.getBucket();
            ensureBucketExists(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(command.objectKey())
                    .stream(command.inputStream(), command.size(), -1)
                    .contentType(command.contentType())
                    .build());
            return new StoredFile(bucketName, command.objectKey(), command.privateObject() ? null : buildPublicUrl(command.objectKey()));
        } catch (Exception exception) {
            throw new BusinessException("文件上传失败");
        }
    }

    @Override
    public void delete(String bucketName, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectKey).build());
        } catch (Exception exception) {
            throw new BusinessException("文件删除失败");
        }
    }

    @Override
    public String generatePresignedGetUrl(String bucketName, String objectKey, Duration expiry) {
        try {
            long seconds = Math.min(7L * 24 * 60 * 60, Math.max(1, expiry.toSeconds()));
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucketName).object(objectKey).expiry((int) Math.min(seconds, Integer.MAX_VALUE)).build());
        } catch (Exception exception) {
            throw new BusinessException("生成文件访问链接失败");
        }
    }

    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName)
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
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
