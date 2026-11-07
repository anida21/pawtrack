package com.pawtrack.pet.dto;

/** Role of a user in a household, as reported by identity. */
public enum HouseholdRole {
    OWNER, MEMBER, SITTER;

    /** SITTER may read pets, but not create, change or delete them (pet.yaml). */
    public boolean canWrite() {
        return this != SITTER;
    }
}
