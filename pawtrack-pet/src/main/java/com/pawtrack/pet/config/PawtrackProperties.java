package com.pawtrack.pet.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** Service settings under the {@code pawtrack.*} prefix in application.yml. */
@Validated
@ConfigurationProperties(prefix = "pawtrack")
public record PawtrackProperties(
    @Valid @NotNull Events events,
    @Valid @NotNull Identity identity,
    @Valid @NotNull Photos photos
) {

    public record Events(@NotBlank String exchange,
                         @NotBlank String deadLetterExchange,
                         @NotBlank String householdQueue) {
    }

    /** Identity service, the authority for household memberships. */
    public record Identity(@NotBlank String url,
                           @NotNull Duration membershipCacheTtl,
                           @NotNull Duration connectTimeout,
                           @NotNull Duration readTimeout) {
    }

    /** MinIO (S3) storage for profile photos. */
    public record Photos(@NotBlank String endpoint,
                         @NotBlank String publicEndpoint,
                         @NotBlank String accessKey,
                         @NotBlank String secretKey,
                         @NotBlank String bucket,
                         @NotNull DataSize maxSize,
                         @NotNull Duration urlValidity) {
    }
}
