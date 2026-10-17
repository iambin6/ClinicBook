package com.nano.clinicbooking.service.gemini;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiClientTest {
    private static final String TEST_KEY = "test-key-not-a-real-credential";
    private static final String ANSWER = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Đã trả lời\"}]}}]}";
    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<ReceivedRequest> requests = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new ReceivedRequest(exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply reply = replies.remove();
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new GeminiClient();
        configure(client);
    }

    private void configure(GeminiClient target) {
        ReflectionTestUtils.setField(target, "apiKey", TEST_KEY);
        ReflectionTestUtils.setField(target, "apiBase", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta");
        ReflectionTestUtils.setField(target, "defaultModel", "configured-model");
        ReflectionTestUtils.setField(target, "fallbackModels", "");
        ReflectionTestUtils.setField(target, "embeddingModel", "configured-embedding");
        ReflectionTestUtils.setField(target, "timeoutMs", 2000);
        ReflectionTestUtils.setField(target, "maxAttempts", 2);
        ReflectionTestUtils.setField(target, "retryDelayMs", 0);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsKeyOnlyInHeaderAndEscapesAllPromptCharacters() throws Exception {
        replies.add(new Reply(200, ANSWER));
        String prompt = "bác sĩ\t\"A\"\n\\line\r\u0001";
        assertThat(client.generateHtml(prompt)).isEqualTo("Đã trả lời");
        assertThat(requests).hasSize(1);
        ReceivedRequest request = requests.getFirst();
        assertThat(request.path()).isEqualTo("/v1beta/models/configured-model:generateContent").doesNotContain(TEST_KEY);
        assertThat(request.key()).isEqualTo(TEST_KEY);
        assertThat(mapper.readTree(request.body()).at("/contents/0/parts/0/text").asText()).isEqualTo(prompt);
    }

    @Test
    void suspendedKeyIsNotRetriedOrLoggedEvenWhenGoogleEchoesIt() {
        replies.add(new Reply(403, "{\"error\":{\"message\":\"Consumer " + TEST_KEY
                + " suspended\",\"details\":[{\"reason\":\"CONSUMER_SUSPENDED\"}]}}"));
        ReflectionTestUtils.setField(client, "fallbackModels", "another-model");
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(client.generateHtml("test")).isNull();
            assertThat(requests).hasSize(1);
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("CONSUMER_SUSPENDED", "AUTHORIZATION").doesNotContain(TEST_KEY);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void retriesRateLimitAndRecovers() {
        replies.add(new Reply(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));
        replies.add(new Reply(200, ANSWER));
        assertThat(client.generateHtml("test")).isEqualTo("Đã trả lời");
        assertThat(requests).hasSize(2);
    }

    @Test
    void repeatedServerErrorsStopAtConfiguredAttemptLimit() {
        replies.add(new Reply(503, "{}"));
        replies.add(new Reply(503, "{}"));
        assertThat(client.generateHtml("test")).isNull();
        assertThat(requests).hasSize(2);
    }

    @Test
    void invalidRequestIsNotRetried() {
        replies.add(new Reply(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}"));
        assertThat(client.generateHtml("test")).isNull();
        assertThat(requests).hasSize(1);
    }

    @Test
    void missingModelUsesOnlyConfiguredFallback() {
        replies.add(new Reply(404, "{}"));
        replies.add(new Reply(200, ANSWER));
        ReflectionTestUtils.setField(client, "fallbackModels", "replacement-model");
        assertThat(client.generateHtml("test")).isEqualTo("Đã trả lời");
        assertThat(requests.get(1).path()).contains("/replacement-model:");
    }

    @Test
    void missingKeyDoesNotSendRequests() {
        ReflectionTestUtils.setField(client, "apiKey", " ");
        assertThat(client.generateHtml("test")).isNull();
        assertThat(client.embed("test", 3, Duration.ofSeconds(1))).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void embeddingUsesConfiguredDimensionsAndHeader() throws Exception {
        replies.add(new Reply(200, "{\"embedding\":{\"values\":[0.1,0.2,0.3]}}"));
        assertThat(client.embed("question", 3, Duration.ofSeconds(1))).hasSize(3);
        ReceivedRequest request = requests.getFirst();
        assertThat(request.path()).isEqualTo("/v1beta/models/configured-embedding:embedContent");
        assertThat(request.key()).isEqualTo(TEST_KEY);
        assertThat(mapper.readTree(request.body()).path("outputDimensionality").asInt()).isEqualTo(3);
    }

    @Test
    void incompatibleEmbeddingIsDiscarded() {
        replies.add(new Reply(200, "{\"embedding\":{\"values\":[0.1]}}"));
        assertThat(client.embed("question", 3, Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void transportTimeoutStopsAfterBoundedRetries() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any())).thenThrow(new HttpTimeoutException("sensitive upstream details"));
        GeminiClient timeoutClient = new GeminiClient(http);
        configure(timeoutClient);
        assertThat(timeoutClient.generateHtml("test")).isNull();
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }

    private record Reply(int status, String body) {}
    private record ReceivedRequest(String path, String key, String body) {}
}
