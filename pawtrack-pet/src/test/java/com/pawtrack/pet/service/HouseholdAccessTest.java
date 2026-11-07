package com.pawtrack.pet.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pawtrack.pet.config.IdentityClient;
import com.pawtrack.pet.dto.HouseholdMembership;
import com.pawtrack.pet.dto.HouseholdRole;
import com.pawtrack.pet.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A household missing from the (possibly stale) cached memberships is looked up once more before denying. */
class HouseholdAccessTest {

    private final IdentityClient identity = mock(IdentityClient.class);
    private final HouseholdAccess access = new HouseholdAccess(identity);
    private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("alice")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

    @Test
    void newHouseholdIsAllowedAfterARefresh() {
        UUID created = UUID.randomUUID();
        when(identity.myHouseholds(jwt))
            .thenReturn(List.of())  // stale cache: household created a moment ago
            .thenReturn(List.of(new HouseholdMembership(created, "New", HouseholdRole.OWNER)));

        assertThatCode(() -> access.requireWrite(jwt, created)).doesNotThrowAnyException();
        verify(identity).evict("alice");
    }

    @Test
    void stillDeniedWhenIdentityConfirmsNoMembership() {
        when(identity.myHouseholds(jwt)).thenReturn(List.of());

        assertThatThrownBy(() -> access.requireRead(jwt, UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
    }
}
