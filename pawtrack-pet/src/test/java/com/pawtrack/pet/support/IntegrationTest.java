package com.pawtrack.pet.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.pawtrack.pet.dto.HouseholdRole;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for API tests: full application, real Postgres + RabbitMQ + MinIO, MockMvc with {@code jwt()},
 * memberships from {@link FakeIdentity}. Published events can be asserted with {@link #awaitEvent}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected FakeIdentity identity;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void resetSharedState() {
        amqpAdmin.purgeQueue(TestcontainersConfiguration.CAPTURE_QUEUE, false);
        identity.setDown(false);
    }

    // ---- events ----------------------------------------------------------------------------------------------

    /** Waits for the first captured event of {@code type} that matches {@code filter}. */
    protected DocumentContext awaitEvent(String type, Predicate<DocumentContext> filter) {
        Instant deadline = Instant.now().plus(EVENT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Message message = rabbitTemplate.receive(TestcontainersConfiguration.CAPTURE_QUEUE, 200);
            if (message == null) {
                continue;
            }
            DocumentContext event = JsonPath.parse(new String(message.getBody(), StandardCharsets.UTF_8));
            if (type.equals(event.read("$.type")) && filter.test(event)) {
                return event;
            }
        }
        return fail("No %s event matching the filter was published within %s", type, EVENT_TIMEOUT);
    }

    /** Filter for {@link #awaitEvent}: {@code data.<field>} equals {@code value} (compared as strings). */
    protected static Predicate<DocumentContext> data(String field, Object value) {
        return event -> String.valueOf(value).equals(String.valueOf(event.read("$.data." + field, Object.class)));
    }

    // ---- setup shortcuts -------------------------------------------------------------------------------------

    /** A new household with {@code owner} as OWNER (identity's side of the world). */
    protected UUID household(TestUser owner) {
        UUID householdId = UUID.randomUUID();
        identity.grant(owner, householdId, HouseholdRole.OWNER);
        return householdId;
    }

    protected static String petJson(UUID householdId, String name, String species) {
        return """
            {"householdId":"%s","name":"%s","species":"%s"}""".formatted(householdId, name, species);
    }

    /** Creates a pet through the API and returns its id. */
    protected String createPet(TestUser user, UUID householdId, String name, String species) throws Exception {
        String body = mvc.perform(post("/api/v1/pets").with(user.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(petJson(householdId, name, species)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
