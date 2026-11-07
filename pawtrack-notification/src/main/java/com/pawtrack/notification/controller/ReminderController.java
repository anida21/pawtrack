package com.pawtrack.notification.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.dto.ReminderRequest;
import com.pawtrack.notification.dto.ReminderResponse;
import com.pawtrack.notification.entity.ReminderStatus;
import com.pawtrack.notification.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reminders")
@RestController
@RequestMapping("/api/v1/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @Operation(summary = "Upcoming reminders for pets in the caller's households (sorted by dueAt).")
    @GetMapping
    public List<ReminderResponse> listReminders(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) UUID petId,
                                                @RequestParam(required = false) ReminderStatus status,
                                                @RequestParam(required = false) Instant from,
                                                @RequestParam(required = false) Instant to) {
        return reminderService.list(jwt, new ReminderService.Filter(petId, status, from, to));
    }

    @Operation(summary = "Custom reminder (source=CUSTOM).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse createReminder(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody ReminderRequest request) {
        return reminderService.create(jwt, request);
    }

    @Operation(summary = "Cancel (status=CANCELLED).")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelReminder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        reminderService.cancel(jwt, id);
    }

    @Operation(summary = "Mark as done (status=DONE).")
    @PostMapping("/{id}/done")
    public ReminderResponse markReminderDone(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reminderService.markDone(jwt, id);
    }
}
