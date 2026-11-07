package com.pawtrack.pet.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** {@code PetPage} schema of pet.yaml (a stable shape instead of Spring's Page JSON). */
public record PetPage(List<PetResponse> content, int page, int size, long totalElements, int totalPages) {

    public static PetPage of(Page<PetResponse> page) {
        return new PetPage(page.getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }
}
