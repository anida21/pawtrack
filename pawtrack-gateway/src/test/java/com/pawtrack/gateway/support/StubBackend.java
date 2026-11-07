package com.pawtrack.gateway.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A fake downstream service on a random local port. It records every request it receives and answers with
 * {@code {"service":"<name>","method":"...","uri":"..."}}: 201 + {@code Location} for POST, 200 otherwise.
 */
public final class StubBackend {

    /** One request as the downstream service saw it. Header names are case-insensitive. */
    public record ReceivedRequest(String method, String uri, Map<String, List<String>> headers, String body) {

        public String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    private final String name;
    private final HttpServer server;
    private final BlockingQueue<ReceivedRequest> received = new LinkedBlockingQueue<>();

    private StubBackend(String name) throws IOException {
        this.name = name;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    public static StubBackend start(String name) {
        try {
            return new StubBackend(name);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start stub backend " + name, e);
        }
    }

    public String name() {
        return name;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The next request this backend received, waiting briefly; {@code null} if none arrived. */
    public ReceivedRequest nextRequest() throws InterruptedException {
        return received.poll(5, TimeUnit.SECONDS);
    }

    public boolean receivedNothing() {
        return received.isEmpty();
    }

    public void reset() {
        received.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(exchange.getRequestHeaders());
        String method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI().toString();
        received.add(new ReceivedRequest(method, uri, headers, body));

        byte[] response = ("{\"service\":\"" + name + "\",\"method\":\"" + method + "\",\"uri\":\"" + uri + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        int status = 200;
        if ("POST".equals(method)) {
            status = 201;
            exchange.getResponseHeaders().add("Location", uri + "/created-1");
        }
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }
}
