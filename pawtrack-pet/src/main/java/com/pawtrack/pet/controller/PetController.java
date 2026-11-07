package com.pawtrack.pet.controller;

import java.net.URI;
import java.util.UUID;

import com.pawtrack.pet.dto.PetPage;
import com.pawtrack.pet.dto.PetRequest;
import com.pawtrack.pet.dto.PetResponse;
import com.pawtrack.pet.entity.Species;
import com.pawtrack.pet.service.PetService;
import com.pawtrack.pet.service.PhotoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** pet.yaml: /api/v1/pets and /api/v1/pets/{petId}[/photo]. */
@RestController
@RequestMapping("/api/v1/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final PhotoService photoService;

    @GetMapping
    public PetPage list(@AuthenticationPrincipal Jwt jwt,
                        @RequestParam(required = false) UUID householdId,
                        @RequestParam(required = false) Species species,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return petService.list(jwt, householdId, species, page, size);
    }

    @PostMapping
    public ResponseEntity<PetResponse> create(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody PetRequest request) {
        PetResponse pet = petService.create(jwt, request);
        return ResponseEntity.created(URI.create("/api/v1/pets/" + pet.id())).body(pet);
    }

    @GetMapping("/{petId}")
    public PetResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) {
        return petService.get(jwt, petId);
    }

    @PutMapping("/{petId}")
    public PetResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                              @Valid @RequestBody PetRequest request) {
        return petService.update(jwt, petId, request);
    }

    @DeleteMapping("/{petId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) {
        petService.delete(jwt, petId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/{petId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PetResponse uploadPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                                   @RequestPart("file") MultipartFile file) {
        return photoService.upload(jwt, petId, file);
    }

    /** 302 to a pre-signed MinIO URL valid 10 minutes. */
    @GetMapping("/{petId}/photo")
    public ResponseEntity<Void> getPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) {
        return ResponseEntity.status(302).location(URI.create(photoService.downloadUrl(jwt, petId))).build();
    }
}
