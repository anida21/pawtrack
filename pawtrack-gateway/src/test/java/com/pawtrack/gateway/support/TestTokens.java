package com.pawtrack.gateway.support;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Real signed JWTs for tests, so requests go through the gateway's actual bearer-token filter and the
 * {@code Authorization} header reaches the backend. Keycloak is replaced by a key pair generated per JVM.
 */
public final class TestTokens {

    public static final String ISSUER = "http://keycloak.test/realms/pawtrack";

    private static final RSAKey REALM_KEY = generateKey();
    private static final RSAKey FOREIGN_KEY = generateKey();

    private TestTokens() {
    }

    /** Valid for five minutes, signed by the "realm" key. */
    public static String valid() {
        Instant now = Instant.now();
        return sign(REALM_KEY, ISSUER, now.minusSeconds(5), now.plusSeconds(300));
    }

    public static String expired() {
        Instant now = Instant.now();
        return sign(REALM_KEY, ISSUER, now.minusSeconds(3600), now.minusSeconds(1800));
    }

    public static String signedByUnknownKey() {
        Instant now = Instant.now();
        return sign(FOREIGN_KEY, ISSUER, now.minusSeconds(5), now.plusSeconds(300));
    }

    public static String fromOtherIssuer() {
        Instant now = Instant.now();
        return sign(REALM_KEY, "http://keycloak.test/realms/other", now.minusSeconds(5), now.plusSeconds(300));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Validates like the production decoder: signature, expiry and issuer. */
    static JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(REALM_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(RSAKey key, String issuer, Instant issuedAt, Instant expiresAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(UUID.randomUUID().toString())
            .claim("preferred_username", "gateway-test")
            .claim("email", "gateway-test@pawtrack.test")
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
