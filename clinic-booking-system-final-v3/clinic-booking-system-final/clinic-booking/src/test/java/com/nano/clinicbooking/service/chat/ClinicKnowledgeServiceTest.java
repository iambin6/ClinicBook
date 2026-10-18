package com.nano.clinicbooking.service.chat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.nano.clinicbooking.service.gemini.GeminiClient;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.JsonWithInt.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClinicKnowledgeServiceTest {
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final QdrantClient qdrant = mock(QdrantClient.class);
    private ClinicKnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new ClinicKnowledgeService(gemini, qdrant, "new-collection", 3, 100);
    }

    private CollectionInfo collection(int dimensions, long points) {
        return CollectionInfo.newBuilder().setPointsCount(points).setConfig(CollectionConfig.newBuilder()
                .setParams(CollectionParams.newBuilder().setVectorsConfig(VectorsConfig.newBuilder()
                        .setParams(VectorParams.newBuilder().setSize(dimensions).setDistance(Distance.Cosine)))))
                .build();
    }

    @Test
    void qdrantTransportDoesNotDependOnUnusedAiSdks() {
        var realTransport = new ClinicKnowledgeService(gemini, true, "unavailable.invalid", 6334,
                true, "smoke-test-only", "new", 3, 100);
        try {
            assertThat(ReflectionTestUtils.getField(realTransport, "qdrant")).isNotNull();
        } finally {
            realTransport.close();
        }
    }

    @Test
    void disabledQdrantNeedsNoCredentialsOrEmbedding() {
        var disabled = new ClinicKnowledgeService(gemini, false, "", 6334, true, "", "new", 3, 100);
        assertThat(disabled.findContext("test")).isEmpty();
        verifyNoInteractions(gemini, qdrant);
    }

    @Test
    void failedCollectionLookupFallsBackBeforeCallingGemini() {
        when(qdrant.getCollectionInfoAsync(anyString(), any(Duration.class)))
                .thenReturn(Futures.immediateFailedFuture(new IllegalStateException("offline")));
        assertThat(service.findContext("test")).isEmpty();
        verifyNoInteractions(gemini);
    }

    @Test
    void unresponsiveQdrantIsCancelledWithinBudget() {
        SettableFuture<CollectionInfo> pending = SettableFuture.create();
        when(qdrant.getCollectionInfoAsync(anyString(), any(Duration.class))).thenReturn(pending);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThat(service.findContext("test")).isEmpty());
        assertThat(pending.isCancelled()).isTrue();
        verifyNoInteractions(gemini);
    }

    @Test
    void incompatibleOldCollectionIsNotSearched() {
        when(qdrant.getCollectionInfoAsync(anyString(), any(Duration.class)))
                .thenReturn(Futures.immediateFuture(collection(1536, 10)));
        assertThat(service.findContext("test")).isEmpty();
        verifyNoInteractions(gemini);
        verify(qdrant, never()).queryAsync(any(QueryPoints.class), any(Duration.class));
    }

    @Test
    void emptyCollectionSkipsEmbedding() {
        when(qdrant.getCollectionInfoAsync(anyString(), any(Duration.class)))
                .thenReturn(Futures.immediateFuture(collection(3, 0)));
        assertThat(service.findContext("test")).isEmpty();
        verifyNoInteractions(gemini);
    }

    @Test
    void failedEmbeddingSkipsSearch() {
        when(qdrant.getCollectionInfoAsync(anyString(), any(Duration.class)))
                .thenReturn(Futures.immediateFuture(collection(3, 10)));
        when(gemini.embed(anyString(), eq(3), any(Duration.class))).thenReturn(List.of());
        assertThat(service.findContext("test")).isEmpty();
        verify(qdrant, never()).queryAsync(any(QueryPoints.class), any(Duration.class));
    }

    @Test
    void searchFailureIsOptional() {
        when(qdrant.getCollectionInfoAsync(anyString(), any(Duration.class)))
                .thenReturn(Futures.immediateFuture(collection(3, 10)));
        when(gemini.embed(anyString(), eq(3), any(Duration.class))).thenReturn(List.of(1f, 0f, 0f));
        when(qdrant.queryAsync(any(QueryPoints.class), any(Duration.class)))
                .thenReturn(Futures.immediateFailedFuture(new IllegalStateException("offline")));
        assertThat(service.findContext("test")).isEmpty();
    }

    @Test
    void compatibleSearchReturnsOnlyTextPayloads() {
        when(qdrant.getCollectionInfoAsync(anyString(), any(Duration.class)))
                .thenReturn(Futures.immediateFuture(collection(3, 10)));
        when(gemini.embed(anyString(), eq(3), any(Duration.class))).thenReturn(List.of(1f, 0f, 0f));
        when(qdrant.queryAsync(any(QueryPoints.class), any(Duration.class))).thenReturn(Futures.immediateFuture(List.of(
                ScoredPoint.newBuilder().putPayload("text", Value.newBuilder().setStringValue("Kiến thức phòng khám").build()).build(),
                ScoredPoint.newBuilder().build())));
        assertThat(service.findContext("test")).isEqualTo("Kiến thức phòng khám");
    }
}
