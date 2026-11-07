package com.pawtrack.notification.config;

import java.time.LocalTime;
import java.time.ZoneId;

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
    @Valid @NotNull Reminders reminders,
    @Valid @NotNull Mail mail
) {

    public record Events(@NotBlank String exchange, @NotBlank String deadLetterExchange) {
    }

    /**
     * @param dispatchCron          when due reminders are emailed ("-" disables)
     * @param medicationTopUpCron   when medication reminders are generated further ahead ("-" disables)
     * @param medicationHorizonDays how far ahead medication reminders exist (rolling)
     * @param vaccinationLeadDays   first vaccination reminder this many days before nextDueDate
     * @param vaccinationTime       local time of vaccination reminders
     * @param defaultMedicationTime used when a medication has no timeOfDay
     * @param defaultTimezone       used when no household member's timezone is known
     */
    public record Reminders(
        @NotBlank String dispatchCron,
        @NotBlank String medicationTopUpCron,
        @Positive int dispatchBatchSize,
        @Positive int medicationHorizonDays,
        @Positive int vaccinationLeadDays,
        @NotNull LocalTime vaccinationTime,
        @NotNull LocalTime defaultMedicationTime,
        @NotNull ZoneId defaultTimezone
    ) {
    }

    public record Mail(@NotBlank String from, @NotBlank String appUrl) {
    }
}
