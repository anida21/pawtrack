package com.pawtrack.pet.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import com.pawtrack.pet.dto.HouseholdRole;
import com.pawtrack.pet.support.IntegrationTest;
import com.pawtrack.pet.support.TestUser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PUT/GET /api/v1/pets/{petId}/photo against a real MinIO. */
class PetPhotoControllerTest extends IntegrationTest {

    private static final byte[] PNG = withPayload(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);
    private static final byte[] JPEG = withPayload(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, 64);
    private static final byte[] WEBP = withPayload("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), 64);

    private final TestUser owner = TestUser.random("Owner");
    private final TestUser stranger = TestUser.random("Stranger");

    @Nested
    class PutPhoto {

        @Test
        void storesPhotoAndRedirectsToIt() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");

            mvc.perform(upload(id, photo("luna.png", MediaType.IMAGE_PNG_VALUE, PNG)).with(owner.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.hasPhoto").value(true));

            Download download = followRedirect(id, owner);
            assertThat(download.status()).isEqualTo(200);
            assertThat(download.contentType()).isEqualTo("image/png");
            assertThat(download.body()).isEqualTo(PNG);
        }

        @Test
        void newUploadReplacesThePhoto() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");
            mvc.perform(upload(id, photo("a.png", MediaType.IMAGE_PNG_VALUE, PNG)).with(owner.jwt()))
                .andExpect(status().isOk());

            mvc.perform(upload(id, photo("b.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG)).with(owner.jwt()))
                .andExpect(status().isOk());

            Download download = followRedirect(id, owner);
            assertThat(download.contentType()).isEqualTo("image/jpeg");
            assertThat(download.body()).isEqualTo(JPEG);
        }

        @Test
        void typeIsTakenFromTheContentNotTheHeader() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");

            // WebP declared as octet-stream is still accepted and stored as image/webp ...
            mvc.perform(upload(id, photo("luna", MediaType.APPLICATION_OCTET_STREAM_VALUE, WEBP)).with(owner.jwt()))
                .andExpect(status().isOk());
            assertThat(followRedirect(id, owner).contentType()).isEqualTo("image/webp");

            // ... while text declared as image/png is rejected.
            byte[] text = "definitely not a picture".getBytes(StandardCharsets.UTF_8);
            mvc.perform(upload(id, photo("fake.png", MediaType.IMAGE_PNG_VALUE, text)).with(owner.jwt()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void unsupportedFormatGets415() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");
            byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);

            mvc.perform(upload(id, photo("luna.gif", MediaType.IMAGE_GIF_VALUE, gif)).with(owner.jwt()))
                .andExpect(status().isUnsupportedMediaType());
            mvc.perform(upload(id, photo("empty.png", MediaType.IMAGE_PNG_VALUE, new byte[0])).with(owner.jwt()))
                .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void nonMultipartBodyGets415() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");
            mvc.perform(put("/api/v1/pets/{id}/photo", id).with(owner.jwt())
                    .contentType(MediaType.IMAGE_PNG).content(PNG))
                .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void photoOverFiveMegabytesGets413() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");
            byte[] big = withPayload(PNG, 5 * 1024 * 1024 + 1 - PNG.length);

            mvc.perform(upload(id, photo("big.png", MediaType.IMAGE_PNG_VALUE, big)).with(owner.jwt()))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            byte[] limit = withPayload(PNG, 5 * 1024 * 1024 - PNG.length);
            mvc.perform(upload(id, photo("limit.png", MediaType.IMAGE_PNG_VALUE, limit)).with(owner.jwt()))
                .andExpect(status().isOk());
        }

        @Test
        void missingFilePartGets400() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");
            mvc.perform(upload(id, photo("wrong-name", "other", MediaType.IMAGE_PNG_VALUE, PNG)).with(owner.jwt()))
                .andExpect(status().isBadRequest());
        }

        @Test
        void sitterAndNonMemberGet403() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            TestUser sitter = TestUser.random("Sitter");
            identity.grant(sitter, home, HouseholdRole.SITTER);

            mvc.perform(upload(id, photo("x.png", MediaType.IMAGE_PNG_VALUE, PNG)).with(sitter.jwt()))
                .andExpect(status().isForbidden());
            mvc.perform(upload(id, photo("x.png", MediaType.IMAGE_PNG_VALUE, PNG)).with(stranger.jwt()))
                .andExpect(status().isForbidden());
        }

        @Test
        void unknownPetGets404() throws Exception {
            mvc.perform(upload(UUID.randomUUID().toString(), photo("x.png", MediaType.IMAGE_PNG_VALUE, PNG))
                    .with(owner.jwt()))
                .andExpect(status().isNotFound());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(upload(UUID.randomUUID().toString(), photo("x.png", MediaType.IMAGE_PNG_VALUE, PNG)))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetPhoto {

        @Test
        void sitterMayViewThePhoto() throws Exception {
            UUID home = household(owner);
            String id = createPet(owner, home, "Luna", "DOG");
            mvc.perform(upload(id, photo("luna.png", MediaType.IMAGE_PNG_VALUE, PNG)).with(owner.jwt()))
                .andExpect(status().isOk());
            TestUser sitter = TestUser.random("Sitter");
            identity.grant(sitter, home, HouseholdRole.SITTER);

            mvc.perform(get("/api/v1/pets/{id}/photo", id).with(sitter.jwt()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("pet-photos/pets/" + id)))
                .andExpect(header().string("Location", containsString("X-Amz-Expires=600")));
        }

        @Test
        void petWithoutPhotoGets404() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");
            mvc.perform(get("/api/v1/pets/{id}/photo", id).with(owner.jwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void unknownOrDeletedPetGets404() throws Exception {
            mvc.perform(get("/api/v1/pets/{id}/photo", UUID.randomUUID()).with(owner.jwt()))
                .andExpect(status().isNotFound());

            String id = createPet(owner, household(owner), "Luna", "DOG");
            mvc.perform(upload(id, photo("luna.png", MediaType.IMAGE_PNG_VALUE, PNG)).with(owner.jwt()))
                .andExpect(status().isOk());
            mvc.perform(delete("/api/v1/pets/{id}", id).with(owner.jwt())).andExpect(status().isNoContent());
            mvc.perform(get("/api/v1/pets/{id}/photo", id).with(owner.jwt())).andExpect(status().isNotFound());
        }

        @Test
        void nonMemberGets403() throws Exception {
            String id = createPet(owner, household(owner), "Luna", "DOG");
            mvc.perform(upload(id, photo("luna.png", MediaType.IMAGE_PNG_VALUE, PNG)).with(owner.jwt()))
                .andExpect(status().isOk());

            mvc.perform(get("/api/v1/pets/{id}/photo", id).with(stranger.jwt()))
                .andExpect(status().isForbidden());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get("/api/v1/pets/{id}/photo", UUID.randomUUID())).andExpect(status().isUnauthorized());
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static MockMultipartHttpServletRequestBuilder upload(String petId, MockMultipartFile file) {
        return multipart(HttpMethod.PUT, "/api/v1/pets/{id}/photo", petId).file(file);
    }

    private static MockMultipartFile photo(String filename, String contentType, byte[] content) {
        return photo(filename, "file", contentType, content);
    }

    private static MockMultipartFile photo(String filename, String part, String contentType, byte[] content) {
        return new MockMultipartFile(part, filename, contentType, content);
    }

    private static byte[] withPayload(byte[] header, int extraBytes) {
        byte[] content = Arrays.copyOf(header, header.length + extraBytes);
        for (int i = header.length; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
    }

    /** GET .../photo, then fetch the pre-signed URL from MinIO like a browser would. */
    private Download followRedirect(String petId, TestUser user) throws Exception {
        String location = mvc.perform(get("/api/v1/pets/{id}/photo", petId).with(user.jwt()))
            .andExpect(status().isFound())
            .andReturn().getResponse().getHeader("Location");
        HttpURLConnection connection = (HttpURLConnection) URI.create(location).toURL().openConnection();
        try (InputStream in = connection.getInputStream()) {
            return new Download(connection.getResponseCode(), connection.getContentType(), in.readAllBytes());
        } catch (IOException ex) {
            return new Download(connection.getResponseCode(), connection.getContentType(), new byte[0]);
        } finally {
            connection.disconnect();
        }
    }

    private record Download(int status, String contentType, byte[] body) {
    }
}
