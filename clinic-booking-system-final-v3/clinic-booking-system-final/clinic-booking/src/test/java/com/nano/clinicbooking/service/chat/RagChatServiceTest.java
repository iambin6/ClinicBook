package com.nano.clinicbooking.service.chat;

import com.google.common.util.concurrent.Futures;
import com.nano.clinicbooking.service.gemini.GeminiClient;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagChatServiceTest {
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final UnifiedClinicDataProvider data = mock(UnifiedClinicDataProvider.class);
    private final ClinicKnowledgeService knowledge = mock(ClinicKnowledgeService.class);
    private final RagChatService chat = new RagChatService(gemini, data, knowledge);

    private void clinicData() {
        when(data.getEventsJson()).thenReturn("[{\"title\":\"Khám định kỳ\"}]");
        when(data.getDoctorsJson()).thenReturn("[{\"fullName\":\"Bác sĩ A\"}]");
        when(data.getShiftsJsonForWeek(any(LocalDate.class))).thenReturn("[]");
    }

    @Test
    void brokenQdrantStillGeneratesAnswerFromClinicData() {
        clinicData();
        QdrantClient qdrant = mock(QdrantClient.class);
        when(qdrant.getCollectionInfoAsync(anyString(), any(Duration.class)))
                .thenReturn(Futures.immediateFailedFuture(new IllegalStateException("Qdrant offline")));
        var brokenKnowledge = new ClinicKnowledgeService(gemini, qdrant, "new", 768, 100);
        var resilientChat = new RagChatService(gemini, data, brokenKnowledge);
        when(gemini.generateHtml(anyString())).thenReturn("Bạn có thể khám với bác sĩ A.");

        assertThat(resilientChat.ask("Có bác sĩ nào? ")).isEqualTo("Bạn có thể khám với bác sĩ A.");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateHtml(prompt.capture());
        assertThat(prompt.getValue()).contains("Bác sĩ A", "Khám định kỳ", "Có bác sĩ nào?");
    }

    @Test
    void unavailableGeminiReturnsFriendlyMessage() {
        clinicData();
        when(knowledge.findContext(anyString())).thenReturn("");
        assertThat(chat.ask("Có bác sĩ nào?")).contains("trợ lý AI hiện chưa thể trả lời");
    }

    @Test
    void internalErrorsAreNotExposedToPatient() {
        when(data.getEventsJson()).thenThrow(new IllegalStateException("private database credentials"));
        assertThat(chat.ask("Có sự kiện nào?")).doesNotContain("private database credentials", "IllegalStateException");
        verifyNoInteractions(gemini);
    }

    @Test
    void greetingDoesNotRequireExternalServices() {
        assertThat(chat.ask("xin chào")).contains("Xin chào bạn");
        verifyNoInteractions(gemini, data, knowledge);
    }

    @Test
    void clinicIntroductionRetainsStaticFallbackWhenGeminiIsDown() {
        assertThat(chat.ask("giới thiệu phòng khám")).contains("Phòng khám Nano Clinic");
        verifyNoInteractions(data, knowledge);
    }
}
