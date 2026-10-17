package com.nano.clinicbooking.service.chat;

import com.nano.clinicbooking.service.gemini.GeminiClient;
import io.grpc.Status;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Optional retrieval: a single time budget covers collection validation, embedding and search. */
@Slf4j
@Service
public class ClinicKnowledgeService {
    private final GeminiClient gemini;
    private final QdrantClient qdrant;
    private final String collection;
    private final int dimensions;
    private final Duration timeout;

    @Autowired
    public ClinicKnowledgeService(
            GeminiClient gemini,
            @Value("${qdrant.enabled:true}") boolean enabled,
            @Value("${qdrant.host:}") String host,
            @Value("${qdrant.port:6334}") int port,
            @Value("${qdrant.tls:true}") boolean tls,
            @Value("${qdrant.api-key:}") String apiKey,
            @Value("${qdrant.collection:nano_knowledge_base_gemini_embedding_2_768}") String collection,
            @Value("${gemini.embedding.dimensions:768}") int dimensions,
            @Value("${qdrant.timeout.ms:5000}") int timeoutMs) {
        this(gemini, createClient(enabled, host, port, tls, apiKey), collection, dimensions, timeoutMs);
    }

    ClinicKnowledgeService(GeminiClient gemini, QdrantClient qdrant, String collection,
                           int dimensions, int timeoutMs) {
        this.gemini = gemini;
        this.qdrant = qdrant;
        this.collection = collection;
        this.dimensions = dimensions;
        this.timeout = Duration.ofMillis(Math.max(1, timeoutMs));
    }

    private static QdrantClient createClient(boolean enabled, String host, int port,
                                              boolean tls, String apiKey) {
        if (!enabled || host == null || host.isBlank()) return null;
        try {
            var builder = QdrantGrpcClient.newBuilder(host, port, tls);
            if (apiKey != null && !apiKey.isBlank()) builder.withApiKey(apiKey);
            return new QdrantClient(builder.build());
        } catch (RuntimeException e) {
            log.warn("Qdrant client unavailable ({}); chatbot will use clinic data", e.getClass().getSimpleName());
            return null;
        }
    }

    public String findContext(String question) {
        if (qdrant == null) return "";
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            var info = await(qdrant.getCollectionInfoAsync(collection, remaining(deadline)), deadline);
            var vectors = info.getConfig().getParams().getVectorsConfig();
            if (!vectors.hasParams() || vectors.getParams().getSize() != dimensions
                    || vectors.getParams().getDistance() != Distance.Cosine) {
                log.warn("Qdrant collection {} is incompatible with {} dimensions/Cosine; reindex into a new collection",
                        collection, dimensions);
                return "";
            }
            if (info.getPointsCount() == 0) return "";
            var vector = gemini.embed(question, dimensions, remaining(deadline));
            if (vector.isEmpty()) return "";
            QueryPoints request = QueryPoints.newBuilder()
                    .setCollectionName(collection)
                    .setQuery(QueryFactory.nearest(vector))
                    .setLimit(5)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build();
            var hits = await(qdrant.queryAsync(request, remaining(deadline)), deadline);
            log.debug("Retrieved {} knowledge passages from Qdrant collection {}", hits.size(), collection);
            return hits.stream()
                    .map(point -> point.getPayloadMap().get("text"))
                    .filter(value -> value != null && !value.getStringValue().isBlank())
                    .map(value -> value.getStringValue())
                    .collect(Collectors.joining("\n---\n"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            Status.Code code = Status.fromThrowable(e).getCode();
            String action = switch (code) {
                case NOT_FOUND -> "Collection missing: initialize knowledge in the configured collection";
                case UNAUTHENTICATED, PERMISSION_DENIED -> "Check QDRANT_API_KEY and its collection permissions";
                case UNAVAILABLE -> "Check cluster is running, endpoint, gRPC port and TLS settings";
                default -> e instanceof TimeoutException ? "Knowledge retrieval timed out" : "Check Qdrant configuration";
            };
            // gRPC descriptions may contain credentials; keep diagnostics limited to safe codes.
            log.warn("Optional knowledge retrieval unavailable: code={}, type={}. {}; chatbot will use clinic data",
                    code, e.getClass().getSimpleName(), action);
            return "";
        }
    }

    private Duration remaining(long deadline) throws TimeoutException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new TimeoutException("Knowledge retrieval deadline exceeded");
        return Duration.ofNanos(nanos);
    }

    private <T> T await(Future<T> future, long deadline) throws Exception {
        try {
            return future.get(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException | InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    @PreDestroy
    public void close() {
        if (qdrant != null) qdrant.close();
    }
}
