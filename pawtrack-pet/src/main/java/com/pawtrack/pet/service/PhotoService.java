package com.pawtrack.pet.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import com.pawtrack.pet.config.PawtrackProperties;
import com.pawtrack.pet.dto.PetResponse;
import com.pawtrack.pet.entity.Pet;
import com.pawtrack.pet.exception.NotFoundException;
import com.pawtrack.pet.exception.PhotoTooLargeException;
import com.pawtrack.pet.exception.UnsupportedPhotoTypeException;
import com.pawtrack.pet.mapper.PetMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** Profile photo upload (members with write access) and download link (any member). */
@Service
public class PhotoService {

    private final PetService petService;
    private final HouseholdAccess access;
    private final PhotoStorage storage;
    private final PetMapper petMapper;
    private final DataSize maxSize;

    public PhotoService(PetService petService, HouseholdAccess access, PhotoStorage storage, PetMapper petMapper,
                        PawtrackProperties properties) {
        this.petService = petService;
        this.access = access;
        this.storage = storage;
        this.petMapper = petMapper;
        this.maxSize = properties.photos().maxSize();
    }

    /**
     * Replaces the pet's photo. The object key is fixed per pet, so a new upload overwrites the old file
     * and a failed database update cannot leave an orphan behind.
     */
    @Transactional
    public PetResponse upload(Jwt jwt, UUID petId, MultipartFile file) {
        Pet pet = petService.activePet(petId);
        access.requireWrite(jwt, pet.getHouseholdId());

        if (file.getSize() > maxSize.toBytes()) {
            throw new PhotoTooLargeException("Photo must not be larger than " + maxSize.toMegabytes() + " MB");
        }
        byte[] content = read(file);
        PhotoType type = PhotoType.detect(content)
            .orElseThrow(() -> new UnsupportedPhotoTypeException("Photo must be a JPEG, PNG or WebP image"));

        String key = storage.keyFor(pet.getId());
        storage.store(key, content, type.mediaType());
        pet.setPhotoKey(key);
        pet.setPhotoContentType(type.mediaType());
        pet.touch();
        return petMapper.toResponse(pet);
    }

    /** Pre-signed MinIO URL for the pet's photo; 404 if the pet has none. */
    @Transactional(readOnly = true)
    public String downloadUrl(Jwt jwt, UUID petId) {
        Pet pet = petService.activePet(petId);
        access.requireRead(jwt, pet.getHouseholdId());
        if (!pet.hasPhoto()) {
            throw new NotFoundException("Pet has no photo");
        }
        return storage.downloadUrl(pet.getPhotoKey());
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read uploaded photo", ex);
        }
    }
}
