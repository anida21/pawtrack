package com.pawtrack.pet.service;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import com.pawtrack.pet.config.PawtrackProperties;
import com.pawtrack.pet.exception.DependencyUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Profile photos in the MinIO bucket {@code pet-photos}; one object per pet, replaced on upload. */
@Slf4j
@Component
public class PhotoStorage {

    private final MinioClient minioClient;
    private final MinioClient urlSigner;
    private final PawtrackProperties.Photos photos;

    public PhotoStorage(@Qualifier("minioClient") MinioClient minioClient,
                        @Qualifier("minioUrlSigner") MinioClient urlSigner,
                        PawtrackProperties properties) {
        this.minioClient = minioClient;
        this.urlSigner = urlSigner;
        this.photos = properties.photos();
    }

    /** docker-compose creates the bucket too; this covers fresh MinIOs (tests, other environments). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(photos.bucket()).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(photos.bucket()).build());
                log.info("Created MinIO bucket {}", photos.bucket());
            }
        } catch (Exception ex) {
            // Not fatal: pets work without photos, uploads report 503 until MinIO is reachable.
            log.warn("Could not check MinIO bucket {}: {}", photos.bucket(), ex.getMessage());
        }
    }

    public String keyFor(UUID petId) {
        return "pets/" + petId;
    }

    public void store(String key, byte[] content, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(photos.bucket())
                .object(key)
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .contentType(contentType)
                .build());
        } catch (Exception ex) {
            throw new DependencyUnavailableException("Photo could not be stored, try again later", ex);
        }
    }

    /** Pre-signed GET URL valid for {@code pawtrack.photos.url-validity} (10 minutes). */
    public String downloadUrl(String key) {
        try {
            return urlSigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(photos.bucket())
                .object(key)
                .expiry((int) photos.urlValidity().toSeconds())
                .build());
        } catch (Exception ex) {
            throw new DependencyUnavailableException("Photo link could not be created, try again later", ex);
        }
    }
}
