package de.bytewright.sticker_classifier.orchestration.llm;

import de.bytewright.sticker_classifier.domain.AppOrchestrationConfig;
import de.bytewright.sticker_classifier.domain.llm.PromptExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PromptRequestContextConfig {

  @Bean
  PromptExecutorService promptExecutorService(AppOrchestrationConfig appOrchestrationConfig) {
    int threadCount = appOrchestrationConfig.getPrompts().getThreadCount();
    return new PromptExecutorService(Executors.newFixedThreadPool(threadCount));
  }
}
