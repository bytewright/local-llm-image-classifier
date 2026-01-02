package de.bytewright.sticker_classifier.adapter.llm_ollama;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaModelWarmer {
  private final OllamaApi ollamaApi;

  @Async
  @EventListener
  public void onApplicationEvent(ContextRefreshedEvent event) {
    log.info("Initializing ollama api");
    List<String> models = List.of(OllamaContextConfig.DEFAULT_MULTIMODAL_MODEL);
    for (String model : models) {
      sendHello(ollamaApi, model);
    }
  }

  private void sendHello(OllamaApi ollamaApi, String model) {
    try {
      log.info("Attempting to warm up model: {}", model);
      var warmupRequest =
          OllamaApi.ChatRequest.builder(model).stream(false)
              .messages(
                  List.of(
                      OllamaApi.Message.builder(OllamaApi.Message.Role.USER)
                          .content("Hello! Are you ready?")
                          .build()))
              .options(
                  OllamaChatOptions.builder().numPredict(-1).build()) // -1 to generate until EOS
              .build();
      OllamaApi.ChatResponse response = ollamaApi.chat(warmupRequest);
      if (response != null && response.message() != null && response.message().content() != null) {
        String content = response.message().content();
        log.info(
            "Model ({}) warmed up. Response: {}",
            model,
            content.substring(0, Math.min(50, content.length())));
      } else {
        log.warn("Model ({}) warmup might have failed or returned no content.", model);
      }
    } catch (Exception e) {
      log.error("Failed to warm up model ({}): {}", model, e.getMessage());
    }
  }
}
