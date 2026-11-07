package com.pawtrack.identity.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Service settings under the {@code pawtrack.*} prefix in application.yml. */
@Validated
@ConfigurationProperties(prefix = "pawtrack")
public record PawtrackProperties(
    @Valid @NotNull Events events,
    @Valid @NotNull Invitations invitations
) {

    /**
     * @param exchange           shared topic exchange all events go to
     * @param deadLetterExchange direct exchange for rejected messages (events.md)
     * @param replayQueue        queue for {@code replay.requested} (backfill of consumers, events.md)
     */
    public record Events(@NotBlank String exchange, @NotBlank String deadLetterExchange,
                         @NotBlank String replayQueue) {
    }

    public record Invitations(@Positive int validityDays) {
    }
}
