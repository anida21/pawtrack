package com.pawtrack.pet.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two clients for the same MinIO: one for uploads from inside the network, one that only signs
 * download URLs with the host browsers can reach. A pre-signed URL is bound to its host, so it has to
 * be signed for the public endpoint. The region is fixed so signing never needs a network call.
 */
@Configuration
public class MinioConfig {

    static final String REGION = "us-east-1";

    @Bean
    MinioClient minioClient(PawtrackProperties properties) {
        return client(properties.photos().endpoint(), properties.photos());
    }

    @Bean
    MinioClient minioUrlSigner(PawtrackProperties properties) {
        return client(properties.photos().publicEndpoint(), properties.photos());
    }

    private static MinioClient client(String endpoint, PawtrackProperties.Photos photos) {
        return MinioClient.builder()
            .endpoint(endpoint)
            .region(REGION)
            .credentials(photos.accessKey(), photos.secretKey())
            .build();
    }
}
