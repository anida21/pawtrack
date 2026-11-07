package com.pawtrack.health.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Service settings under the {@code pawtrack.*} prefix in application.yml. */
@Validated
@ConfigurationProperties(prefix = "pawtrack")
public record PawtrackProperties(
    @Valid @NotNull Events events,
    @Valid @NotNull Pet pet
) {

    public record Events(@NotBlank String exchange,
                         @NotBlank String deadLetterExchange,
                         @NotBlank String petQueue,
                         boolean replayOnStartup) {
    }

    /** Pet service, the authority for "may this caller see this pet" (health.yaml access rule). */
    public record Pet(@NotBlank String url,
                      @NotNull Duration connectTimeout,
                      @NotNull Duration readTimeout) {
    }
}
