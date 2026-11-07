package com.pawtrack.identity.dto;

import com.pawtrack.identity.entity.HouseholdRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvitationRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotNull InvitableRole role
) {

    /** Roles that can be granted by invitation. OWNER cannot. */
    public enum InvitableRole {
        MEMBER,
        SITTER;

        public HouseholdRole toHouseholdRole() {
            return HouseholdRole.valueOf(name());
        }
    }
}
