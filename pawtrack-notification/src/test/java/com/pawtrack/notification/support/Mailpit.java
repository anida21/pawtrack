package com.pawtrack.notification.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

import static org.awaitility.Awaitility.await;

/**
 * Reads what the service sent through Mailpit's REST API.
 * Uses HttpURLConnection, not the JDK HttpClient (its NIO pipe is broken on some Windows machines).
 */
public class Mailpit {

    /** One received email. */
    public record Mail(String to, String subject, String text) {
    }

    private final GenericContainer<?> container;
    private RestClient client;

    Mailpit(GenericContainer<?> container) {
        this.container = container;
    }

    /** All emails addressed to {@code address}, oldest first. */
    public List<Mail> to(String address) {
        String list = client().get().uri("/api/v1/search?limit=200&query={q}", "to:\"" + address + "\"")
            .retrieve().body(String.class);
        List<String> ids = new ArrayList<>(JsonPath.<List<String>>read(list, "$.messages[*].ID"));
        Collections.reverse(ids);   // Mailpit lists newest first
        return ids.stream().map(this::read).toList();
    }

    /** Waits until at least {@code count} emails to {@code address} have arrived and returns them. */
    public List<Mail> awaitMails(String address, int count) {
        await().atMost(Duration.ofSeconds(10)).until(() -> to(address).size() >= count);
        return to(address);
    }

    private Mail read(String id) {
        String body = client().get().uri("/api/v1/message/{id}", id).retrieve().body(String.class);
        List<Map<String, Object>> to = JsonPath.read(body, "$.To");
        return new Mail(String.valueOf(to.get(0).get("Address")),JsonPath.read(body, "$.Subject"),
            JsonPath.read(body, "$.Text"));
    }

    private RestClient client() {
        if (client == null) {
            client = RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl("http://" + container.getHost() + ":"
                    + container.getMappedPort(TestcontainersConfiguration.MAILPIT_API_PORT))
                .build();
        }
        return client;
    }
}
