package com.nano.clinicbooking.service.chat;

import com.nano.clinicbooking.service.gemini.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatbotConfigurationTest {
    @Test
    void chatbotBeansStartWithOptionalCloudCredentialsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(GeminiClient.class, ClinicKnowledgeService.class, RagChatService.class)
                .withBean(UnifiedClinicDataProvider.class, () -> mock(UnifiedClinicDataProvider.class))
                .withPropertyValues("qdrant.enabled=false", "gemini.api.key=")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RagChatService.class);
                    assertThat(context.getBean(RagChatService.class).ask("chào")).contains("Xin chào bạn");
                });
    }
}
