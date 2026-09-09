package com.playmate.space.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinioFileStorageServiceTest {

    @Test
    void rewritesOnlyThePublicOriginAndPreservesThePresignedQuery() throws Exception {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://playmate-minio:9000");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setBucket("playmate-files");
        properties.setPrivateBucket("playmate-private-files");
        properties.setPublicBaseUrl("http://115.159.47.212/minio");
        MinioFileStorageService service = new MinioFileStorageService(properties);

        Method method = MinioFileStorageService.class.getDeclaredMethod("rewriteForPublicAccess", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service,
                "http://playmate-minio:9000/playmate-private-files/a%20b.jpg?X-Amz-Signature=abc%2Fdef&X-Amz-SignedHeaders=host");

        assertEquals("http://115.159.47.212/minio/playmate-private-files/a%20b.jpg?X-Amz-Signature=abc%2Fdef&X-Amz-SignedHeaders=host", result);
    }
}
