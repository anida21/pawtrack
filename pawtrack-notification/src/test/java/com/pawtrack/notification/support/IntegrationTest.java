package com.pawtrack.notification.support;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.pawtrack.notification.config.PawtrackProperties;
import com.pawtrack.notification.entity.Reminder;
import com.pawtrack.notification.entity.ReminderSource;
import com.pawtrack.notification.repository.ProcessedEventRepository;
import com.pawtrack.notification.repository.ReminderRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests: full application, real Postgres + RabbitMQ + Mailpit, MockMvc with {@code jwt()}.
 * The local copies (users, memberships, pets) are filled the real way, by publishing events to
 * {@code pawtrack.events} and waiting until the listener has processed them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final Duration EVENT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected Mailpit mailpit;

    @Autowired
    protected ReminderRepository reminderRepository;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private PawtrackProperties properties;

    /** A household as notification knows it: members (first = OWNER) and one pet. */
    protected record TestHousehold(UUID id, UUID petId, List<TestUser> members) {
        public TestUser owner() {
            return members.get(0);
        }
    }

    // ---- events ----------------------------------------------------------------------------------------------

    /** Publishes an event like the producers do and returns its eventId (does not wait). */
    protected UUID send(String type, Map<String, ?> data) {
        return send(UUID.randomUUID(), type, data);
    }

    protected UUID send(UUID eventId, String type, Map<String, ?> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("type", type);
        envelope.put("occurredAt", Instant.now());
        envelope.put("producer", "test");
        envelope.put("data", data);
        sendRaw(type, jsonMapper.writeValueAsBytes(envelope));
        return eventId;
    }

    protected void sendRaw(String routingKey, byte[] body) {
        // Like a real producer: Spring adds __TypeId__ with a class that only exists in the sending service.
        Message message = MessageBuilder.withBody(body)
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setHeader("__TypeId__", "com.pawtrack.pet.messaging.EventEnvelope")
            .build();
        rabbitTemplate.send(properties.events().exchange(), routingKey, message);
    }

    /** Publishes an event and waits until it has been processed. */
    protected UUID publish(String type, Map<String, ?> data) {
        UUID eventId = send(type, data);
        awaitProcessed(eventId);
        return eventId;
    }

    protected void awaitProcessed(UUID eventId) {
        await().atMost(EVENT_TIMEOUT).until(() -> processedEvents.existsById(eventId));
    }

    /** Shorthand for event data; values may be null (left out of the map). */
    protected static Map<String, Object> data(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (keysAndValues[i + 1] != null) {
                map.put((String) keysAndValues[i], keysAndValues[i + 1]);
            }
        }
        return map;
    }

    // ---- local copies ----------------------------------------------------------------------------------------

    protected void userUpdated(TestUser user, String timezone, boolean emailNotifications) {
        publish("user.updated", data("userId", user.id(), "email", user.email(), "displayName", user.name(),
            "timezone", timezone, "emailNotifications", emailNotifications));
    }

    protected void memberAdded(UUID householdId, TestUser user, String role) {
        publish("household.member.added", data("householdId", householdId, "userId", user.id(), "role", role));
    }

    protected UUID petCreated(UUID householdId, String name) {
        UUID petId = UUID.randomUUID();
        publish("pet.created", data("petId", petId, "householdId", householdId, "name", name, "species", "DOG"));
        return petId;
    }

    /** New household with one pet; the first user is OWNER, the others MEMBER; all want emails (UTC). */
    protected TestHousehold household(TestUser... users) {
        UUID householdId = UUID.randomUUID();
        for (int i = 0; i < users.length; i++) {
            userUpdated(users[i], "UTC", true);
            memberAdded(householdId, users[i], i == 0 ? "OWNER" : "MEMBER");
        }
        UUID petId = petCreated(householdId, "Rex");
        return new TestHousehold(householdId, petId, List.of(users));
    }

    // ---- API shortcuts ---------------------------------------------------------------------------------------

    /** Creates a custom reminder through the API and returns its id. */
    protected UUID createReminder(TestUser user, UUID petId, String title, Instant dueAt) throws Exception {
        String body = mvc.perform(post("/api/v1/reminders").with(user.jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"petId":"%s","title":"%s","dueAt":"%s"}""".formatted(petId, title, dueAt)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected List<Reminder> remindersOf(ReminderSource source, UUID sourceId) {
        return reminderRepository.findBySourceAndSourceIdOrderByDueAt(source, sourceId);
    }
}
