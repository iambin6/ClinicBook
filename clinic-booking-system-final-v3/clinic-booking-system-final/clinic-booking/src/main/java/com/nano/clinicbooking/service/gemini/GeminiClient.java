package com.nano.clinicbooking.service.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiClient {
    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.5-flash-lite}")
    private String defaultModel;

    @Value("${gemini.fallback-models:}")
    private String fallbackModels;

    @Value("${gemini.embedding.model:gemini-embedding-2}")
    private String embeddingModel;

    @Value("${gemini.api.base:https://generativelanguage.googleapis.com/v1beta}")
    private String apiBase;

    @Value("${gemini.timeout.ms:20000}")
    private int timeoutMs;

    @Value("${gemini.max-attempts:2}")
    private int maxAttempts;

    @Value("${gemini.retry-delay.ms:400}")
    private int retryDelayMs;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public GeminiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    GeminiClient(HttpClient http) {
        this.http = http;
    }

    public String generateHtml(String prompt) {
        var models = new LinkedHashSet<String>();
        models.add(defaultModel);
        if (fallbackModels != null) models.addAll(Arrays.asList(fallbackModels.split(",")));
        return generateHtml(prompt, models.stream().filter(m -> m != null && !m.isBlank()).toList());
    }

    // Preserve the null-on-failure contract used by chatbot and email templates.
    public String generateHtml(String prompt, List<String> models) {
        if (!isConfigured()) return null;
        Map<String, Object> body = Map.of("contents", List.of(Map.of(
                "role", "user", "parts", List.of(Map.of("text", prompt == null ? "" : prompt)))));
        for (String model : new LinkedHashSet<>(models)) {
            if (model == null || model.isBlank()) continue;
            ApiResult result = call(model.trim(), "generateContent", body,
                    Duration.ofMillis(Math.max(1, timeoutMs)), Math.clamp(maxAttempts, 1, 3));
            if (result.status() == 200) {
                StringBuilder text = new StringBuilder();
                for (JsonNode part : result.body().path("candidates").path(0).path("content").path("parts")) {
                    text.append(part.path("text").asText(""));
                }
                if (!text.isEmpty()) return text.toString().trim();
                log.warn("Gemini returned no text for model {}", model);
            } else if (result.status() != 404) {
                // Changing models cannot fix invalid credentials, exhausted quota or transport errors.
                return null;
            }
        }
        return null;
    }

    public List<Float> embed(String text, int dimensions, Duration timeout) {
        if (!isConfigured()) return List.of();
        ApiResult result = call(embeddingModel, "embedContent", Map.of(
                "model", "models/" + embeddingModel,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "outputDimensionality", dimensions), timeout, 1);
        if (result.status() != 200) return List.of();
        JsonNode values = result.body().path("embedding").path("values");
        if (!values.isArray() || values.size() != dimensions) {
            log.warn("Gemini embedding dimensions do not match configured size {}", dimensions);
            return List.of();
        }
        var vector = new java.util.ArrayList<Float>(dimensions);
        for (JsonNode value : values) {
            if (!value.isNumber() || !Float.isFinite(value.floatValue())) return List.of();
            vector.add(value.floatValue());
        }
        return vector;
    }

    private boolean isConfigured() {
        if (apiKey != null && !apiKey.isBlank()) return true;
        log.warn("Gemini is unavailable: GEMINI_API_KEY is missing");
        return false;
    }

    private ApiResult call(String model, String operation, Map<String, Object> body,
                           Duration timeout, int attempts) {
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiBase.replaceAll("/+$", "") + "/models/" + model + ":" + operation))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey.trim())
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) return new ApiResult(status, mapper.readTree(response.body()));
                // Google's error message may echo the API key. Log only machine-readable codes.
                log.warn("Gemini {} failed: HTTP {}, category={}, reason={}", operation, status,
                        category(status), errorReason(response.body()));
                if (!isTransient(status) || attempt == attempts) return new ApiResult(status, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ApiResult(0, null);
            } catch (IOException e) {
                log.warn("Gemini {} transport failure: {} (attempt {}/{})",
                        operation, e.getClass().getSimpleName(), attempt, attempts);
                if (attempt == attempts) return new ApiResult(0, null);
            } catch (RuntimeException e) {
                log.error("Gemini {} configuration failure: {}", operation, e.getClass().getSimpleName());
                return new ApiResult(0, null);
            }
            try {
                Thread.sleep(Math.max(0, retryDelayMs) * (1L << (attempt - 1)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ApiResult(0, null);
            }
        }
        return new ApiResult(0, null);
    }

    private boolean isTransient(int status) {
        return status == 408 || status == 429 || (status >= 500 && status < 600);
    }

    private String category(int status) {
        return switch (status) {
            case 401, 403 -> "AUTHORIZATION";
            case 404 -> "MODEL_NOT_FOUND";
            case 429 -> "QUOTA_OR_RATE_LIMIT";
            case 408 -> "TIMEOUT";
            default -> status >= 500 ? "UPSTREAM_SERVER" : "INVALID_REQUEST";
        };
    }

    private String errorReason(String body) {
        try {
            JsonNode error = mapper.readTree(body).path("error");
            for (JsonNode detail : error.path("details")) {
                String reason = detail.path("reason").asText("");
                if (reason.matches("[A-Z][A-Z0-9_]{0,63}")) return reason;
            }
            String status = error.path("status").asText("");
            if (status.matches("[A-Z][A-Z0-9_]{0,63}")) return status;
        } catch (Exception ignored) {
            // The upstream body is intentionally never included in logs.
        }
        return "UNKNOWN";
    }

    private record ApiResult(int status, JsonNode body) {}
}
