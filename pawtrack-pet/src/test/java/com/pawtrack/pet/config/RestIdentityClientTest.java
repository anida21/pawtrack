package com.pawtrack.pet.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.pawtrack.pet.dto.HouseholdMembership;
import com.pawtrack.pet.dto.HouseholdRole;
import com.pawtrack.pet.exception.DependencyUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The real HTTP lookup against identity: token forwarding, response mapping and the 60 s cache. */
class RestIdentityClientTest {

    private static final String URL = "http://identity.test/api/v1/me/households";

    private final MutableClock clock = new MutableClock();
    private MockRestServiceServer identity;
    private RestIdentityClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://identity.test");
        identity = MockRestServiceServer.bindTo(builder).build();
        client = new RestIdentityClient(builder.build(), properties(), clock);
    }

    @Test
    void forwardsTheCallersTokenAndMapsTheMemberships() {
        UUID householdId = UUID.randomUUID();
        identity.expect(requestTo(URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-of-alice"))
            .andRespond(withSuccess("""
                [{"householdId":"%s","householdName":"My pets","role":"SITTER"}]""".formatted(householdId),
                MediaType.APPLICATION_JSON));

        List<HouseholdMembership> memberships = client.myHouseholds(jwt("token-of-alice", "alice"));

        assertThat(memberships).containsExactly(new HouseholdMembership(householdId, "My pets", HouseholdRole.SITTER));
        identity.verify();
    }

    @Test
    void answersFromTheCacheUntilTheTtlHasPassed() {
        identity.expect(ExpectedCount.twice(), requestTo(URL))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.myHouseholds(jwt("t1", "alice"));
        clock.advance(Duration.ofSeconds(59));
        client.myHouseholds(jwt("t2", "alice"));   // cached
        clock.advance(Duration.ofSeconds(1));
        client.myHouseholds(jwt("t3", "alice"));   // expired → second request

        identity.verify();
    }

    @Test
    void cachesPerUser() {
        identity.expect(ExpectedCount.twice(), requestTo(URL))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.myHouseholds(jwt("t1", "alice"));
        client.myHouseholds(jwt("t2", "bob"));

        identity.verify();
    }

    @Test
    void identityFailureIsReportedAsUnavailableAndNotCached() {
        identity.expect(requestTo(URL)).andRespond(withServerError());
        identity.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.myHouseholds(jwt("t1", "alice")))
            .isInstanceOf(DependencyUnavailableException.class);
        assertThat(client.myHouseholds(jwt("t1", "alice"))).isEmpty();
        identity.verify();
    }

    private static Jwt jwt(String token, String subject) {
        return Jwt.withTokenValue(token).header("alg", "none").subject(subject).build();
    }

    private static PawtrackProperties properties() {
        return new PawtrackProperties(
            new PawtrackProperties.Events("pawtrack.events", "pawtrack.events.dlx", "pet.household-events",
                "pet.replay-requests"),
            new PawtrackProperties.Identity("http://identity.test", Duration.ofSeconds(60),
                Duration.ofSeconds(1), Duration.ofSeconds(1)),
            new PawtrackProperties.Photos("http://minio", "http://minio", "key", "secret", "pet-photos",
                DataSize.ofMegabytes(5), Duration.ofMinutes(10)));
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-07T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
