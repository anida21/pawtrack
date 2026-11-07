package com.pawtrack.health.config;

import java.util.UUID;

import com.pawtrack.health.dto.PetRef;
import com.pawtrack.health.exception.DependencyUnavailableException;
import com.pawtrack.health.exception.ForbiddenException;
import com.pawtrack.health.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The real HTTP access check against pet: token forwarding, response mapping and error translation. */
class RestPetClientTest {

    private static final UUID PET_ID = UUID.randomUUID();
    private static final String URL = "http://pet.test/api/v1/pets/" + PET_ID;

    private MockRestServiceServer pet;
    private RestPetClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://pet.test");
        pet = MockRestServiceServer.bindTo(builder).build();
        client = new RestPetClient(builder.build());
    }

    @Test
    void forwardsTheCallersTokenAndReadsThePet() {
        UUID householdId = UUID.randomUUID();
        pet.expect(requestTo(URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-of-alice"))
            .andRespond(withSuccess("""
                {"id":"%s","householdId":"%s","name":"Luna","species":"DOG","hasPhoto":false,
                 "createdAt":"2026-11-07T10:00:00Z"}""".formatted(PET_ID, householdId), MediaType.APPLICATION_JSON));

        PetRef ref = client.getPet(jwt("token-of-alice"), PET_ID);

        assertThat(ref).isEqualTo(new PetRef(PET_ID, householdId, "Luna"));
        pet.verify();
    }

    @Test
    void notFoundIsPassedOn() {
        pet.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.getPet(jwt("t"), PET_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void forbiddenIsPassedOn() {
        pet.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.getPet(jwt("t"), PET_ID)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anyOtherAnswerMeansUnavailable() {
        pet.expect(requestTo(URL)).andRespond(withServerError());
        pet.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        pet.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> client.getPet(jwt("t"), PET_ID))
                .isInstanceOf(DependencyUnavailableException.class);
        }
        pet.verify();
    }

    private static Jwt jwt(String token) {
        return Jwt.withTokenValue(token).header("alg", "none").subject("alice").build();
    }
}
