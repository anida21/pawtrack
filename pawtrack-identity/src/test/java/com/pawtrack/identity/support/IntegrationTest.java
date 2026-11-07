package com.pawtrack.identity.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for API tests: full application, real Postgres + RabbitMQ, MockMvc with {@code jwt()}.
 * Published events can be asserted with {@link #awaitEvent}.
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
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void purgeCapturedEvents() {
        amqpAdmin.purgeQueue(TestcontainersConfiguration.CAPTURE_QUEUE, false);
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

    // ---- API shortcuts ---------------------------------------------------------------------------------------

    /** Provisions {@code user} (first call to /me). */
    protected void signIn(TestUser user) throws Exception {
        mvc.perform(get("/api/v1/me").with(user.jwt())).andExpect(status().isOk());
    }

    protected String createHousehold(TestUser owner, String name) throws Exception {
        String body = mvc.perform(post("/api/v1/households").with(owner.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Creates an invitation and returns its code. */
    protected String invite(TestUser owner, String householdId, String email, String role) throws Exception {
        String body = mvc.perform(post("/api/v1/households/{id}/invitations", householdId).with(owner.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    /** Invites {@code member} and lets them accept, so they join with {@code role}. */
    protected void join(TestUser owner, String householdId, TestUser member, String role) throws Exception {
        String code = invite(owner, householdId, member.email(), role);
        mvc.perform(post("/api/v1/invitations/{code}/accept", code).with(member.jwt()))
            .andExpect(status().isOk());
    }
}
