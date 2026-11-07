package com.pawtrack.health.controller;

import java.util.UUID;
import java.util.stream.Stream;

import com.pawtrack.health.support.Bodies;
import com.pawtrack.health.support.IntegrationTest;
import com.pawtrack.health.support.TestUser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * health.yaml access rule, checked for every operation: pet decides (FakePets stands in for
 * {@code GET /api/v1/pets/{petId}}), 404 and 403 are passed on, an unreachable pet is 503.
 * Requests carry valid bodies, so only the access rule can fail them.
 */
class AccessRulesTest extends IntegrationTest {

    private final TestUser owner = TestUser.random("Owner");
    private final TestUser stranger = TestUser.random("Stranger");

    record Endpoint(HttpMethod method, String suffix, String body) {

        MockHttpServletRequestBuilder build(String petId) {
            String path = "/api/v1/pets/" + petId + "/" + suffix.replace("{id}", UUID.randomUUID().toString());
            MockHttpServletRequestBuilder builder = request(method, path);
            return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        @Override
        public String toString() {
            return method + " " + suffix;
        }
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
            new Endpoint(HttpMethod.GET, "vaccinations", null),
            new Endpoint(HttpMethod.POST, "vaccinations", Bodies.VACCINATION),
            new Endpoint(HttpMethod.PUT, "vaccinations/{id}", Bodies.VACCINATION),
            new Endpoint(HttpMethod.DELETE, "vaccinations/{id}", null),
            new Endpoint(HttpMethod.GET, "vet-visits", null),
            new Endpoint(HttpMethod.POST, "vet-visits", Bodies.VET_VISIT),
            new Endpoint(HttpMethod.PUT, "vet-visits/{id}", Bodies.VET_VISIT),
            new Endpoint(HttpMethod.DELETE, "vet-visits/{id}", null),
            new Endpoint(HttpMethod.GET, "medications", null),
            new Endpoint(HttpMethod.POST, "medications", Bodies.MEDICATION),
            new Endpoint(HttpMethod.PUT, "medications/{id}", Bodies.MEDICATION),
            new Endpoint(HttpMethod.DELETE, "medications/{id}", null),
            new Endpoint(HttpMethod.GET, "weights", null),
            new Endpoint(HttpMethod.POST, "weights", Bodies.WEIGHT),
            new Endpoint(HttpMethod.GET, "summary", null)
        ).map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void withoutTokenGets401(Endpoint endpoint) throws Exception {
        UUID petId = pets.create(owner, "Luna");
        mvc.perform(endpoint.build(petId.toString()))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void callerWithoutAccessToThePetGets403(Endpoint endpoint) throws Exception {
        UUID petId = pets.create(owner, "Luna");
        mvc.perform(endpoint.build(petId.toString()).with(stranger.jwt()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void unknownPetGets404(Endpoint endpoint) throws Exception {
        mvc.perform(endpoint.build(UUID.randomUUID().toString()).with(owner.jwt()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void unreachablePetGets503(Endpoint endpoint) throws Exception {
        UUID petId = pets.create(owner, "Luna");
        pets.setDown(true);
        mvc.perform(endpoint.build(petId.toString()).with(owner.jwt()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void malformedPetIdGets400(Endpoint endpoint) throws Exception {
        mvc.perform(endpoint.build("not-a-uuid").with(owner.jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void malformedRecordIdGets400(Endpoint endpoint) throws Exception {
        if (!endpoint.suffix().contains("{id}")) {
            return;
        }
        UUID petId = pets.create(owner, "Luna");
        String path = "/api/v1/pets/" + petId + "/" + endpoint.suffix().replace("{id}", "nope");
        MockHttpServletRequestBuilder builder = request(endpoint.method(), path).with(owner.jwt());
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        mvc.perform(builder).andExpect(status().isBadRequest());
    }
}
